# Ejecuta el playbook de Ansible dentro de un contenedor Docker (Ansible no corre nativo en Windows).
# Uso:  powershell -ExecutionPolicy Bypass -File infra\desplegar-ansible.ps1 [-PuertoSsh 443]
# - PuertoSsh: 22 por defecto; usar 443 en redes que bloquean el puerto 22 (la VM escucha en ambos).
# - Contrasenas: el playbook conserva las que ya existen en la VM; en una instalacion nueva
#   usa $env:DB_PASSWORD / $env:GRAFANA_ADMIN_PASSWORD si estan definidas o genera unas aleatorias.
# - La llave privada se monta solo-lectura y se copia dentro del contenedor con permisos 600.
param(
    [string]$Llave = "$env:USERPROFILE\.ssh\admin_proyectos_azure",
    [ValidateSet(22, 443)][int]$PuertoSsh = 22
)

$ErrorActionPreference = "Stop"
$dirAnsible = Join-Path $PSScriptRoot "ansible"

if (-not (Test-Path (Join-Path $dirAnsible "inventario.ini"))) { throw "Falta inventario.ini: ejecuta primero terraform apply" }
if (-not (Test-Path $Llave)) { throw "No existe la llave privada $Llave" }

docker build -q -t admin-proyectos-ansible $dirAnsible | Out-Null
if ($LASTEXITCODE -ne 0) { throw "No se pudo construir la imagen de Ansible (¿Docker Desktop esta encendido?)" }
docker run --rm -e DB_PASSWORD -e GRAFANA_ADMIN_PASSWORD `
    -v "${dirAnsible}:/ansible" `
    -v "${Llave}:/tmp/llave:ro" `
    admin-proyectos-ansible `
    sh -c "mkdir -p /root/.ssh && cp /tmp/llave /root/.ssh/id_vm && chmod 600 /root/.ssh/id_vm && ANSIBLE_CONFIG=/ansible/ansible.cfg ansible-playbook -e ansible_port=$PuertoSsh playbook.yml"
if ($LASTEXITCODE -ne 0) { throw "ansible-playbook termino con errores" }
