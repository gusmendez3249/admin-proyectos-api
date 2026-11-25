# Migracion unica del laboratorio local de CI (Docker Desktop, proyecto "devsecops") a la VM de CI.
# Empaqueta los volumenes de Jenkins y SonarQube y los sube a /opt/ci-respaldo en la VM;
# despues, infra\desplegar-ansible.ps1 -Playbook ci los restaura antes del primer arranque.
#
# Uso:  powershell -ExecutionPolicy Bypass -File infra\migrar-ci.ps1 [-PuertoSsh 443]
# Requisitos: el laboratorio local DETENIDO (docker compose -p devsecops stop) para copiar datos consistentes.
#
# Se excluye lo regenerable: workspaces y caches de Jenkins (~2.5 GB) y el indice de SonarQube
# (se reconstruye desde la BD). Los respaldos contienen credenciales cifradas de Jenkins: viajan
# solo por SSH y NO se versionan.
param(
    [string]$Llave = "$env:USERPROFILE\.ssh\admin_proyectos_azure",
    [ValidateSet(22, 443)][int]$PuertoSsh = 22,
    [string]$ProyectoLocal = "devsecops"
)

$ErrorActionPreference = "Stop"
$tfdir = Join-Path $PSScriptRoot "terraform"
$ip = (terraform -chdir="$tfdir" output -raw ip_ci)
if (-not $ip) { throw "No hay IP del servidor de CI: ejecuta terraform apply" }

$corriendo = docker ps --filter "label=com.docker.compose.project=$ProyectoLocal" --format "{{.Names}}"
if ($corriendo) { throw "Deten primero el laboratorio local: docker compose -p $ProyectoLocal stop ($corriendo)" }

$tmp = Join-Path $env:TEMP "ci-respaldo"
New-Item -ItemType Directory -Force $tmp | Out-Null
$volumenes = @{
    "jenkins_home"         = "--exclude=./workspace --exclude=./caches --exclude=./logs --exclude=./.cache"
    "sonarqube_db"         = ""
    "sonarqube_extensions" = ""
}
foreach ($v in $volumenes.Keys) {
    Write-Host "Empaquetando $v ..."
    docker run --rm -v "${ProyectoLocal}_${v}:/v:ro" -v "${tmp}:/b" alpine sh -c "cd /v && tar -czpf /b/$v.tgz $($volumenes[$v]) ."
    if ($LASTEXITCODE -ne 0) { throw "No se pudo empaquetar $v" }
}
Get-ChildItem $tmp -Filter *.tgz | ForEach-Object { "{0,-28} {1,8:N1} MB" -f $_.Name, ($_.Length / 1MB) }

$ssh = @("-p", "$PuertoSsh", "-i", $Llave, "-o", "StrictHostKeyChecking=accept-new")
$scp = @("-P", "$PuertoSsh", "-i", $Llave, "-o", "StrictHostKeyChecking=accept-new")
& ssh @ssh "azureuser@$ip" "mkdir -p /tmp/ci-respaldo"
& scp @scp (Join-Path $tmp "*.tgz") "azureuser@${ip}:/tmp/ci-respaldo/"
if ($LASTEXITCODE -ne 0) { throw "Fallo la copia a la VM" }
& ssh @ssh "azureuser@$ip" "sudo mkdir -p /opt/ci-respaldo && sudo mv /tmp/ci-respaldo/*.tgz /opt/ci-respaldo/ && sudo chmod 600 /opt/ci-respaldo/*.tgz && sudo ls -la /opt/ci-respaldo"
Remove-Item -Recurse -Force $tmp
Write-Host "Listo. Ahora ejecuta: infra\desplegar-ansible.ps1 -Playbook ci -PuertoSsh $PuertoSsh"
