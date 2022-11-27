.PHONY: build protobufs protos static migrations emails chart

build: protobufs chart

protobufs: protos protos/__init__.py

protos:
	poetry run python -m grpc_tools.protoc \
		--proto_path=. \
		--python_out=. \
		--grpc_python_out=. \
		--pyi_out=. \
		protos/*.proto

protos/__init__.py:
	touch $@

static:
	python manage.py collectstatic --no-input

migrations:
	python manage.py migrate

emails:
	npx mjml users/templates/*.html.mjml -c.minify=true -o users/templates

chart:
	cp kubernetes/fyreplace/Chart.template.yaml kubernetes/fyreplace/Chart.yaml
	echo "appVersion: $(shell git describe --tags)" >> kubernetes/fyreplace/Chart.yaml
