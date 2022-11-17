FROM python:3.11

WORKDIR /app

COPY requirements.txt .
RUN python -m pip install --no-cache-dir --upgrade pip wheel setuptools poetry
RUN poetry self add "poetry-dynamic-versioning[plugin]"
RUN poetry install

COPY . .
RUN make protobufs
