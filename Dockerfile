FROM python:3.11 AS build

WORKDIR /app

RUN python -m venv --copies .venv
RUN pip install --no-cache-dir --upgrade poetry
RUN poetry config virtualenvs.create false

COPY poetry.lock pyproject.toml .docker-venv.sh ./
RUN ./.docker-venv.sh poetry install --no-interaction


FROM node:lts AS build-emails

WORKDIR /app

COPY . ./
RUN make emails


FROM python:3.11-slim AS run

ENV PYTHONUNBUFFERED 1
WORKDIR /app

COPY .docker-dependencies-debian.sh .
RUN ./.docker-dependencies-debian.sh

COPY --from=build-emails /app /app
COPY --from=build /app/.venv /app/.venv
RUN ./.docker-venv.sh python manage.py collectstatic --no-input

EXPOSE 8000
CMD ["./.docker-venv.sh", "python", "-u", "-m", "daphne", "core.asgi:application", "--bind", "0.0.0.0"]
