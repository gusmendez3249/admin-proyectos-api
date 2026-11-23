variable "subscription_id" {
  description = "ID de la suscripcion de Azure (az account show --query id -o tsv)"
  type        = string
}

variable "prefijo" {
  description = "Prefijo para nombrar todos los recursos"
  type        = string
  default     = "gusmendez-devops"
}

variable "ubicacion" {
  description = "Region de Azure (Azure for Students solo permite: northcentralus, canadacentral, chilecentral, swedencentral, norwayeast)"
  type        = string
  default     = "northcentralus"
}

variable "tamano_vm" {
  description = "Tamano de la VM: 2 vCPU y 4 GB (AMD, la opcion mas economica que alcanza para app + monitoreo)"
  type        = string
  default     = "Standard_B2als_v2"
}

variable "usuario_admin" {
  description = "Usuario Linux de la VM (acceso solo con llave SSH)"
  type        = string
  default     = "azureuser"
}

variable "ruta_llave_publica" {
  description = "Llave SSH publica que se instala en la VM; la privada nunca sale del equipo local"
  type        = string
  default     = "~/.ssh/admin_proyectos_azure.pub"
}

variable "ip_admin" {
  description = "IP (CIDR) autorizada para SSH y puertos de administracion. Vacio = se detecta la IP publica actual"
  type        = string
  default     = ""
}

variable "puertos_publicos" {
  description = "Puertos abiertos a Internet: front (8082) y Grafana (3000)"
  type        = list(number)
  default     = [8082, 3000]
}

variable "etiquetas" {
  description = "Etiquetas comunes para identificar y controlar costos"
  type        = map(string)
  default = {
    proyecto   = "admin-proyectos"
    materia    = "gestion-proceso-desarrollo-software"
    gestionado = "terraform"
  }
}
