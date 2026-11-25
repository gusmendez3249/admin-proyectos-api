# Ejecuta un playbook de Ansible dentro de un contenedor Docker (Ansible no corre nativo en Windows).
# Uso:  powershell -ExecutionPolicy Bypass -File infra\desplegar-ansible.ps1 [-Playbook app|ci] [-PuertoSsh 443]
# - Playbook: app = VM de la aplicacion (playbook.yml); ci = VM de Jenkins + SonarQube (playbook-ci.yml).
# - PuertoSsh: 22 por defecto; usar 443 en redes que bloquean el puerto 22 (las VMs escuchan en ambos).
# - Contrasenas: los playbooks conservan las que ya existen en la VM; en una instalacion nueva
#   usan variables de entorno si estan definidas o generan unas aleatorias.
# - La llave privada se monta solo-lectura y se copia dentro del contenedor con permisos 600.
param(
    [string]$Llave = "$env:USERPROFILE\.ssh\admin_proyectos_azure",
    [ValidateSet("app", "ci")][string]$Playbook = "app",
    [ValidateSet(22, 443)][int]$PuertoSsh = 22
)

$ErrorActionPreference = "Stop"
$dirAnsible = Join-Path $PSScriptRoot "ansible"
$archivo = @{ app = "playbook.yml"; ci = "playbook-ci.yml" }[$Playbook]

if (-not (Test-Path (Join-Path $dirAnsible "inventario.ini"))) { throw "Falta inventario.ini: ejecuta primero terraform apply" }
if (-not (Test-Path $Llave)) { throw "No existe la llave privada $Llave" }

docker build -q -t admin-proyectos-ansible $dirAnsible | Out-Null
if ($LASTEXITCODE -ne 0) { throw "No se pudo construir la imagen de Ansible (¿Docker Desktop esta encendido?)" }
docker run --rm -e DB_PASSWORD -e GRAFANA_ADMIN_PASSWORD -e SONAR_DB_PASSWORD `
    -v "${dirAnsible}:/ansible" `
    -v "${Llave}:/tmp/llave:ro" `
    admin-proyectos-ansible `
    sh -c "mkdir -p /root/.ssh && cp /tmp/llave /root/.ssh/id_vm && chmod 600 /root/.ssh/id_vm && ANSIBLE_CONFIG=/ansible/ansible.cfg ansible-playbook -e ansible_port=$PuertoSsh $archivo"
if ($LASTEXITCODE -ne 0) { throw "ansible-playbook termino con errores" }
