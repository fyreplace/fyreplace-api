FROM python:3-slim

RUN apt-get update
RUN apt-get install -y make gcc default-libmysqlclient-dev libpq-dev libmagic1

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .
RUN make all
CMD ["python", "manage.py", "grpc"]
