FROM python:3

WORKDIR /app

COPY requirements.txt .
RUN python -m pip install --no-cache-dir --upgrade pip wheel
RUN python -m pip install --no-cache-dir --requirement requirements.txt

COPY . .
RUN make
