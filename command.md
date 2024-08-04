
docker build -t alpine-graalvm .
docker build --no-cache -t alpine-graalvm .
docker build -t alpine-graalvm-build .

docker run --rm -it --entrypoint /bin/bash alpine-graalvm-build 
docker run --rm -it --entrypoint sh alpine-graalvm 
docker run --name test-app -it -p 8080:8080 alpine-graalvm


## Inspect the Docker Image Layers
Pull the Image:
```shell
docker pull ghcr.io/graalvm/native-image-community:22
```

Inspect the Image:
Use the docker image inspect command to get detailed information about the image.

```shell
docker image inspect ghcr.io/graalvm/native-image-community:22
```