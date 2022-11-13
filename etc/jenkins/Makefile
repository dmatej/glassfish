SHELL := /bin/bash
IMAGE_TAG = 17.0.5_8

.PHONY: clean docker-clean docker-build docker-push

default: docker-build

clean: docker-clean

docker-clean:
	docker rmi dmatej/glassfish-builder:$(IMAGE_TAG)

docker-build:
	docker build -t dmatej/glassfish-builder:$(IMAGE_TAG) .

docker-push:
	docker push dmatej/glassfish-builder:$(IMAGE_TAG)
