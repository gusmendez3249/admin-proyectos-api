# Ejecuta el playbook de Ansible dentro de un contenedor Docker.
# Uso:  powershell -ExecutionPolicy Bypass -File infra\desplegar-ansible.ps1
# - Pide la contrasena de la BD sin mostrarla (o la toma de $env:DB_PASSWORD).
# - La llave privada se monta solo-lectura y se copia dentro del contenedor con permisos 600.
param([string]$Llave = "$env:USERPROFILE\.ssh\admin_proyectos_azure")

$ErrorActionPreference = "Stop"
$dirAnsible = Join-Path $PSScriptRoot "ansible"

if (-not (Test-Path (Join-Path $dirAnsible "inventario.ini"))) { throw "Falta inventario.ini: ejecuta primero terraform apply" }
if (-not (Test-Path $Llave)) { throw "No existe la llave privada $Llave" }

if (-not $env:DB_PASSWORD) {
    $seguro = Read-Host "Contrasena de la base de datos (min. 12 caracteres)" -AsSecureString
    $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro))
}

docker build -q -t admin-proyectos-ansible $dirAnsible | Out-Null
if ($LASTEXITCODE -ne 0) { throw "No se pudo construir la imagen de Ansible (¿Docker Desktop esta encendido?)" }
docker run --rm -e DB_PASSWORD `
    -v "${dirAnsible}:/ansible" `
    -v "${Llave}:/tmp/llave:ro" `
    admin-proyectos-ansible `
    sh -c "mkdir -p /root/.ssh && cp /tmp/llave /root/.ssh/id_vm && chmod 600 /root/.ssh/id_vm && ANSIBLE_CONFIG=/ansible/ansible.cfg ansible-playbook playbook.yml"
