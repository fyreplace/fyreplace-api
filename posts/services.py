from typing import Iterator
from uuid import UUID

import grpc
from django.contrib.contenttypes.models import ContentType
from django.db.transaction import atomic
from google.protobuf import empty_pb2
from grpc_interceptor.exceptions import InvalidArgument, PermissionDenied

from core.authentication import no_auth
from core.pagination import PaginatorMixin
from core.services import ImageUploadMixin
from core.utils import make_uuid
from notifications.models import delete_notifications_for
from notifications.tasks import report_content
from protos import (
    comment_pb2,
    comment_pb2_grpc,
    id_pb2,
    image_pb2,
    pagination_pb2,
    post_pb2,
    post_pb2_grpc,
)

from .models import Chapter, Comment, Post, Stack, Subscription, Vote
from .pagination import (
    ArchivePaginationAdapter,
    CommentsPaginationAdapter,
    DraftsPaginationAdapter,
    OwnPostsPaginationAdapter,
)
from .signals import fetched


class PostService(PaginatorMixin, post_pb2_grpc.PostServiceServicer):
    @no_auth
    def ListFeed(
        self, request_iterator: Iterator[post_pb2.Vote], context: grpc.ServicerContext
    ) -> Iterator[post_pb2.Post]:
        posts = []
        fetch_after = None
        end_reached = False

        def refill_stack():
            nonlocal posts
            nonlocal fetch_after

            if context.caller:
                with atomic():
                    stack = Stack.objects.select_for_update().get(
                        id=context.caller.stack.id
                    )
                    stack.fill()

                current_ids = [str(p.id) for p in posts]
                new_posts = context.caller.stack.posts.exclude(id__in=current_ids)
            elif fetch_after:
                new_posts = Post.active_objects.filter(date_published__gt=fetch_after)
            else:
                new_posts = Post.active_objects.all()

            new_posts = new_posts.order_by("date_published")

            if not context.caller:
                new_posts = new_posts[: Stack.MAX_SIZE]

            new_posts = list(new_posts.select_related())

            if len(new_posts) > 0:
                fetch_after = new_posts[-1].date_published

            posts += new_posts

        refill_stack()
        yield from (p.to_message(context=context) for p in posts[:3])

        for request in request_iterator:
            if len(posts) == 0:
                return

            if context.caller:
                Vote.objects.create(
                    user=context.caller,
                    post_id=UUID(bytes=request.post_id),
                    spread=request.spread,
                )

            posts = [p for p in posts if p.id.bytes != request.post_id]
            post_count = len(posts)
            should_refill = post_count < 3

            if not should_refill:
                yield posts[2].to_message(context=context)
            elif not end_reached:
                refill_stack()
                yield posts[-1 if len(posts) < 3 else 2].to_message(context=context)

                if len(posts) < Stack.MAX_SIZE:
                    end_reached = True

    def ListArchive(
        self,
        request_iterator: Iterator[pagination_pb2.Page],
        context: grpc.ServicerContext,
    ) -> Iterator[post_pb2.Posts]:
        posts = Post.published_objects.filter(subscribers=context.caller)
        return self.paginate(
            request_iterator,
            bundle_class=post_pb2.Posts,
            adapter=ArchivePaginationAdapter(context, posts),
            message_overrides={"is_preview": True},
        )

    def ListOwnPosts(
        self,
        request_iterator: Iterator[pagination_pb2.Page],
        context: grpc.ServicerContext,
    ):
        posts = Post.published_objects.filter(author=context.caller)
        return self.paginate(
            request_iterator,
            bundle_class=post_pb2.Posts,
            adapter=OwnPostsPaginationAdapter(context, posts),
            message_overrides={"is_preview": True},
        )

    def ListDrafts(
        self,
        request_iterator: Iterator[pagination_pb2.Page],
        context: grpc.ServicerContext,
    ):
        drafts = Post.draft_objects.filter(author=context.caller)
        return self.paginate(
            request_iterator,
            bundle_class=post_pb2.Posts,
            adapter=DraftsPaginationAdapter(context, drafts),
            message_overrides={"is_preview": True},
        )

    def Retrieve(
        self, request: id_pb2.Id, context: grpc.ServicerContext
    ) -> post_pb2.Post:
        post = Post.existing_objects.select_related().get_readable_by(
            context.caller, id__bytes=request.id
        )
        overrides = {}

        if post.is_anonymous and context.caller.id != post.author_id:
            overrides["author"] = None

        if post.subscribers.filter(id=context.caller.id).exists():
            overrides["is_subscribed"] = True

        if subscription := post.subscriptions.filter(user=context.caller).first():
            if comment := subscription.last_comment_seen:
                overrides["comments_read"] = (
                    Comment.objects.filter(
                        post=post, date_created__lte=comment.date_created
                    )
                    .exclude(date_created=comment.date_created, id__gt=comment.id)
                    .count()
                )

        return post.to_message(context=context, **overrides)

    def Create(
        self, request: empty_pb2.Empty, context: grpc.ServicerContext
    ) -> id_pb2.Id:
        post = Post.objects.create(author=context.caller)
        return id_pb2.Id(id=post.id.bytes)

    @atomic
    def Publish(
        self, request: post_pb2.Publication, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        post = Post.existing_objects.select_for_update().get_writable_by(
            context.caller, id__bytes=request.id
        )

        post.publish(anonymous=request.anonymous)
        return empty_pb2.Empty()

    @atomic
    def Delete(
        self, request: id_pb2.Id, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        post = Post.existing_objects.select_for_update().get_readable_by(
            context.caller, id__bytes=request.id
        )

        if context.caller.id != post.author_id and not context.caller.is_staff:
            raise PermissionDenied("invalid_post_author")

        post.delete()
        return empty_pb2.Empty()

    def UpdateSubscription(
        self, request: post_pb2.Subscription, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        post = Post.existing_objects.get_readable_by(
            context.caller, id__bytes=request.id
        )

        if not post.date_published:
            raise PermissionDenied("post_not_published")

        if request.subscribed:
            Subscription.objects.get_or_create(
                user=context.caller,
                post=post,
                defaults={"last_comment_seen": post.comments.last()},
            )
        else:
            post.subscribers.remove(context.caller)

        return empty_pb2.Empty()

    def Report(
        self, request: id_pb2.Id, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        post = Post.existing_objects.get_published_readable_by(
            context.caller, id__bytes=request.id
        )

        if post.author == context.caller:
            raise PermissionDenied("invalid_post")

        report_content.delay(
            content_type_id=ContentType.objects.get_for_model(Post).id,
            target_id=post.id,
            reporter_id=str(context.caller.id),
        )
        return empty_pb2.Empty()

    def Absolve(
        self, request: id_pb2.Id, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        if not context.caller.is_staff:
            raise PermissionDenied("caller_rank_insufficient")

        post = Post.existing_objects.get_readable_by(
            context.caller, id__bytes=request.id
        )

        if post.author_id == context.caller.id:
            raise PermissionDenied("caller_owns_post")

        delete_notifications_for(post)
        return empty_pb2.Empty()


class ChapterService(ImageUploadMixin, post_pb2_grpc.ChapterServiceServicer):
    def Create(
        self, request: post_pb2.ChapterLocation, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        post = Post.existing_objects.get_writable_by(
            context.caller, id__bytes=request.post_id
        )
        chapter_count = post.chapters.count()

        if request.position > chapter_count:
            raise InvalidArgument("invalid_position")
        elif chapter_count >= Post.MAX_CHAPTERS:
            raise PermissionDenied("too_many_chapters")

        Chapter.objects.create(
            post=post, position=post.chapter_position(request.position)
        )
        return empty_pb2.Empty()

    @atomic
    def Move(
        self, request: post_pb2.ChapterRelocation, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        post = Post.existing_objects.get_writable_by(
            context.caller, id__bytes=request.post_id
        )
        chapter = post.get_chapter_at(request.from_position, for_update=True)
        new_position = request.to_position

        if new_position > request.from_position:
            new_position += 1

        chapter.position = post.chapter_position(new_position)
        chapter.save()
        return empty_pb2.Empty()

    @atomic
    def UpdateText(
        self, request: post_pb2.ChapterTextUpdate, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        post = Post.existing_objects.get_writable_by(
            context.caller, id__bytes=request.location.post_id
        )
        chapter = post.get_chapter_at(request.location.position, for_update=True)
        chapter.clear(save=False)
        chapter.text = request.text
        chapter.is_title = request.is_title
        chapter.full_clean()
        chapter.save()
        return empty_pb2.Empty()

    @atomic
    def UpdateImage(
        self,
        request_iterator: Iterator[post_pb2.ChapterImageUpdate],
        context: grpc.ServicerContext,
    ) -> image_pb2.Image:
        try:
            location = next(request_iterator).location
        except:
            raise InvalidArgument("missing_location")

        image = self.get_image(request_iterator, chunkator=lambda u: u.chunk)
        post = Post.existing_objects.get_writable_by(
            context.caller, id__bytes=location.post_id
        )
        chapter = post.get_chapter_at(location.position, for_update=True)
        chapter.clear(save=False)
        self.set_image(chapter, "image", image)
        return chapter.to_message().image

    @atomic
    def Delete(
        self, request: post_pb2.ChapterLocation, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        post = Post.existing_objects.get_writable_by(
            context.caller, id__bytes=request.post_id
        )
        post.get_chapter_at(request.position, for_update=True).delete()
        return empty_pb2.Empty()


class CommentService(PaginatorMixin, comment_pb2_grpc.CommentServiceServicer):
    def List(
        self,
        request_iterator: Iterator[pagination_pb2.Page],
        context: grpc.ServicerContext,
    ) -> Iterator[comment_pb2.Comments]:
        def on_items(comments: list[Comment]):
            comments = sorted(comments, key=lambda c: c.date_created)
            pk_set = set(c.id for c in comments)
            fetched.send(sender=Comment, user=context.caller, pk_set=pk_set)

        comments = Comment.objects.all()
        return self.paginate(
            request_iterator,
            bundle_class=comment_pb2.Comments,
            adapter=CommentsPaginationAdapter(context, comments),
            on_items=on_items,
        )

    def Create(
        self, request: comment_pb2.CommentCreation, context: grpc.ServicerContext
    ) -> id_pb2.Id:
        post = Post.existing_objects.get_published_readable_by(
            context.caller, id__bytes=request.post_id
        )

        if context.caller.id in post.author.blocked_users.values_list("id", flat=True):
            raise PermissionDenied("caller_blocked")
        elif len(request.text) == 0:
            raise InvalidArgument("comment_empty")

        comment = Comment.objects.create(
            post=post, author=context.caller, text=request.text
        )
        Subscription.objects.filter(user=context.caller, post=post).update(
            last_comment_seen=comment
        )
        return id_pb2.Id(id=comment.id.bytes)

    @atomic
    def Delete(
        self, request: id_pb2.Id, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        comment = Comment.objects.select_for_update().get(id__bytes=request.id)

        if context.caller != comment.author and not context.caller.is_staff:
            raise PermissionDenied("invalid_comment_author")

        comment.delete()
        return empty_pb2.Empty()

    def Aknowledge(
        self, request: id_pb2.Id, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        comment = Comment.objects.get(id__bytes=request.id)
        Subscription.objects.filter(
            user=context.caller,
            post_id=comment.post_id,
        ).exclude(last_comment_seen__date_created__gt=comment.date_created,).exclude(
            last_comment_seen__date_created=comment.date_created,
            last_comment_seen_id__lt=comment.id,
        ).update(
            last_comment_seen=comment
        )
        return empty_pb2.Empty()

    def Report(
        self, request: id_pb2.Id, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        comment = Comment.objects.get(id__bytes=request.id)

        if comment.author == context.caller:
            raise PermissionDenied("invalid_comment")
        elif comment.is_deleted:
            raise PermissionDenied("comment_deleted")

        report_content.delay(
            content_type_id=ContentType.objects.get_for_model(Comment).id,
            target_id=str(make_uuid(data=request.id)),
            reporter_id=str(context.caller.id),
        )
        return empty_pb2.Empty()

    def Absolve(
        self, request: id_pb2.Id, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        if not context.caller.is_staff:
            raise PermissionDenied("caller_rank_insufficient")

        comment = Comment.existing_objects.get(id__bytes=request.id)

        if comment.author_id == context.caller.id:
            raise PermissionDenied("caller_owns_comment")

        delete_notifications_for(comment)
        return empty_pb2.Empty()
