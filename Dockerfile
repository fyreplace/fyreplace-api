FROM python:3.11

WORKDIR /app

COPY poetry.lock .
RUN python -m pip install --upgrade pip wheel setuptools poetry

COPY pyproject.toml .
RUN poetry install --no-interaction

COPY . .
RUN poetry run python manage.py collectstatic --no-input

EXPOSE 8000
CMD ["poetry", "run", "daphne", "core.asgi:application", "--bind", "0.0.0.0"]
