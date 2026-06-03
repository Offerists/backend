FROM ubuntu:latest
LABEL authors="akela"

ENTRYPOINT ["top", "-b"]