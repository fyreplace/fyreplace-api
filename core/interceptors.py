from importlib import import_module
from inspect import getmembers
from typing import Any, Callable, List, Type

import grpc
import rollbar
from django.core.exceptions import ObjectDoesNotExist, PermissionDenied, ValidationError
from django.db.utils import DataError, IntegrityError
from google.protobuf.message import Message
from grpc_interceptor.exceptions import GrpcException, Unauthenticated
from grpc_interceptor.server import ServerInterceptor

from .services import get_servicer_interfaces


def make_method_name(package_name: str, service_name: str, method_name: str) -> str:
    return f"/{package_name}.{service_name}/{method_name}"


def get_scope(full_method_name: str) -> str:
    return full_method_name.split("/")[:-1]


class ExceptionInterceptor(ServerInterceptor):
    def intercept(
        self,
        method: Callable,
        request: Message,
        context: grpc.ServicerContext,
        method_name: str,
    ) -> Any:
        try:
            return super().intercept(method, request, context, method_name)
        except PermissionDenied as e:
            context.set_code(grpc.StatusCode.PERMISSION_DENIED)
            context.set_details(str(e))
        except (ValidationError, DataError, IntegrityError) as e:
            context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
            context.set_details(str(e))
        except ObjectDoesNotExist as e:
            context.set_code(grpc.StatusCode.NOT_FOUND)
            context.set_details(str(e))
        except GrpcException as e:
            context.set_code(e.status_code)
            context.set_details(e.details)

            if e.status_code in [
                grpc.StatusCode.UNIMPLEMENTED,
                grpc.StatusCode.INTERNAL,
                grpc.StatusCode.UNAVAILABLE,
                grpc.StatusCode.UNKNOWN,
                grpc.StatusCode.RESOURCE_EXHAUSTED,
                grpc.StatusCode.FAILED_PRECONDITION,
                grpc.StatusCode.ABORTED,
                grpc.StatusCode.OUT_OF_RANGE,
                grpc.StatusCode.DATA_LOSS,
            ]:
                self._report_error(request, context, method_name)
        except Exception as e:
            context.set_code(grpc.StatusCode.UNKNOWN)
            context.set_details(str(e))
            self._report_error(request, context, method_name, level="critical")

    def _report_error(
        self,
        request: Message,
        context: grpc.ServicerContext,
        method_name: str,
        level: str = "error",
    ):
        extras = {
            "method": method_name,
            "request": request,
            "context_metadata": context.invocation_metadata(),
            "user_id": str(context.caller.id) if context.caller else None,
        }
        rollbar.report_exc_info(extra_data=extras, level=level)


class AuthorizationInterceptor(ServerInterceptor):
    def __init__(self, services: List[Type[Any]]):
        self.no_auth_method_names = []

        for service in services:
            for servicer in get_servicer_interfaces(service):
                service_name = servicer.__name__[: -len("Servicer")]
                module_name = servicer.__module__.replace("pb2_grpc", "pb2")
                module = import_module(module_name)
                package_name = module.DESCRIPTOR.package

                for member_name in [
                    name for name, _ in getmembers(servicer) if hasattr(service, name)
                ]:
                    member = getattr(service, member_name)

                    if "no_auth" in getattr(member, "__dict__", {}):
                        name = make_method_name(package_name, service_name, member_name)
                        self.no_auth_method_names.append(name)

    def intercept(
        self,
        method: Callable,
        request: Message,
        context: grpc.ServicerContext,
        method_name: str,
    ) -> Any:
        from .grpc import store_user

        user = store_user(context)

        if not user and method_name not in self.no_auth_method_names:
            raise Unauthenticated("missing_credentials")

        return super().intercept(method, request, context, method_name)
