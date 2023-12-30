FROM python:3.12

WORKDIR /app

COPY poetry.lock .
RUN python -m pip install --no-cache-dir --upgrade pip wheel setuptools poetry
COPY pyproject.toml .
RUN poetry install

COPY . .
RUN make protobufs
