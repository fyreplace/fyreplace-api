FROM python:3.13

WORKDIR /app

COPY poetry.lock .
RUN python -m pip install --no-cache-dir --upgrade pip wheel setuptools poetry
COPY pyproject.toml .
RUN poetry install --no-root

COPY . .
RUN make protobufs
