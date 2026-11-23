# Imagen para ejecutar Ansible desde Windows sin instalarlo (Ansible no corre nativo en Windows)
FROM python:3.12-slim
RUN apt-get update && apt-get install -y --no-install-recommends openssh-client \
    && rm -rf /var/lib/apt/lists/* \
    && pip install --no-cache-dir "ansible-core==2.17.*"
WORKDIR /ansible
