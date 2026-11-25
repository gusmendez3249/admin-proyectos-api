# Jenkins con JDK 21 + Semgrep + SQLFluff + cliente PostgreSQL + plugins para SonarQube y JaCoCo.
# Practica 3: se agregan postgresql-client (psql) y SQLFluff para el pipeline de base de datos.
# Misma version de Jenkins que la imagen anterior (2.587) para no migrar jenkins_home.
FROM jenkins/jenkins:2.587-jdk21

USER root

# Semgrep y SQLFluff son herramientas Python: se instalan en un entorno virtual propio
RUN apt-get update \
 && apt-get install -y --no-install-recommends python3 python3-venv postgresql-client \
 && rm -rf /var/lib/apt/lists/* \
 && python3 -m venv /opt/devsecops \
 && /opt/devsecops/bin/pip install --no-cache-dir semgrep==1.182.0 sqlfluff==4.4.0 \
 && ln -s /opt/devsecops/bin/semgrep /usr/local/bin/semgrep \
 && ln -s /opt/devsecops/bin/sqlfluff /usr/local/bin/sqlfluff

USER jenkins

# Plugins con version EXACTA (plugins.txt, 96 plugins tomados del Jenkins que funciona):
# sin versiones fijas, cada build de la imagen descargaba la ultima version de "coverage",
# incompatible con el resto de plugins, y el paso recordCoverage dejaba de existir (bitacora #21).
# sonar    -> withSonarQubeEnv / waitForQualityGate
# coverage -> recordCoverage (publica el reporte JaCoCo)
COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt --latest false
