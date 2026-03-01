# Ryuk
# See https://hub.docker.com/r/testcontainers/ryuk/tags
#
# IMPORTANT! When updating the version for Ryuk in this Dockerfile,
# make sure to update `TESTCONTAINERS_RYUK_CONTAINER_IMAGE` env variable set as part of maven-failsafe-plugin configuration.
#
# 0.13.0
FROM docker.io/testcontainers/ryuk:0.13.0@sha256:31b31269d06603366cbfd0284708dcd2e281e8a4188e53fce3d3304439d0df3d
