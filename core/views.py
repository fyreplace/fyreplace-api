from django.http import HttpRequest, HttpResponse


def health(request: HttpRequest) -> HttpResponse:
    return HttpResponse(status=200)
