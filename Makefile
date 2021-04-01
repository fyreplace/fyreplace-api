.PHONY: all protos

all: protos protos/__init__.py

protos:
	python -m grpc_tools.protoc \
		--proto_path=. \
		--python_out=. \
		--grpc_python_out=. \
		--mypy_out=quiet:. \
		protos/*.proto

protos/__init__.py:
	touch $@
