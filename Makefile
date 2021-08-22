.PHONY: all protos emails static migrations

all: static migrations protos protos/__init__.py

static:
	python manage.py collectstatic --no-input

migrations:
	python manage.py migrate

protos:
	python -m grpc_tools.protoc \
		--proto_path=. \
		--python_out=. \
		--grpc_python_out=. \
		--mypy_out=quiet:. \
		protos/*.proto

protos/__init__.py:
	touch $@

emails:
	npx mjml users/templates/*.mjml -c.minify=true -o users/templates
