FROM python:3-slim

RUN apt-get update
RUN apt-get install -y make gcc default-libmysqlclient-dev libpq-dev libmagic1

WORKDIR /app

COPY requirements.txt .
RUN python -m pip install --no-cache-dir --upgrade pip setuptools wheel
RUN python -m pip install --no-cache-dir --requirement requirements.txt

COPY . .
RUN make
