.PHONY: protobufs static migrations emails

protobufs: protos protos/__init__.py

protos:
	python -m grpc_tools.protoc \
		--proto_path=. \
		--python_out=. \
		--grpc_python_out=. \
		--mypy_out=quiet:. \
		protos/*.proto

protos/__init__.py:
	touch $@

static:
	python manage.py collectstatic --no-input

migrations:
	python manage.py migrate

emails:
	npx mjml users/templates/*.mjml -c.minify=true -o users/templates
