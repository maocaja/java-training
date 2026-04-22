# Deploy Plan — De Local a AWS

## Como usar este documento

Este documento es una guia completa para desplegar tu aplicacion (property-api + pricing-api + api-gateway) en AWS, partiendo de cero conocimiento de cloud. Cada fase explica:

- **El concepto:** que es y por que existe
- **El problema que resuelve:** que se hacia antes y por que no servia
- **Como lo vas a usar:** pasos concretos
- **Como sabes que terminaste:** criterio de exito
- **Preguntas de entrevista:** que te pueden preguntar sobre este tema

Lee cada fase completa antes de empezarla. Si un concepto no esta claro, no avances.

---

## Tabla de contenidos

- [Parte 1: Fundamentos](#parte-1-fundamentos)
  - [Que es la nube y por que importa](#que-es-la-nube-y-por-que-importa)
  - [Que es AWS](#que-es-aws)
  - [Glosario de conceptos clave](#glosario-de-conceptos-clave)
- [Parte 2: Arquitectura objetivo](#parte-2-arquitectura-objetivo)
- [Parte 3: Fases](#parte-3-fases)
  - [Fase 0: Preparacion](#fase-0-preparacion)
  - [Fase 1: IAM y cuenta AWS](#fase-1-iam-y-cuenta-aws)
  - [Fase 2: VPC y networking](#fase-2-vpc-y-networking)
  - [Fase 3: Docker y ECR](#fase-3-docker-y-ecr)
  - [Fase 4: ECS Fargate](#fase-4-ecs-fargate)
  - [Fase 5: RDS + Flyway + Secrets Manager](#fase-5-rds--flyway--secrets-manager)
  - [Fase 5b: DynamoDB](#fase-5b-dynamodb)
  - [Fase 6: ALB + HTTPS](#fase-6-alb--https)
  - [Fase 7: Observabilidad con CloudWatch](#fase-7-observabilidad-con-cloudwatch)
  - [Fase 8: Messaging asincronico (SQS + SNS + Lambda)](#fase-8-messaging-asincronico)
  - [Fase 8b: Streaming con Kinesis](#fase-8b-streaming-con-kinesis)
  - [Fase 9: CloudFront + WAF](#fase-9-cloudfront--waf)
  - [Fase 10: Terraform (Infrastructure as Code)](#fase-10-terraform)
  - [Fase 11: GitHub Actions CI/CD](#fase-11-github-actions-cicd)
  - [Fase 12: Testing BDD con Cucumber](#fase-12-testing-bdd-con-cucumber)
- [Parte 4: Apendice](#parte-4-apendice)

---

# Parte 1: Fundamentos

## Que es la nube y por que importa

**Antes de la nube:** para correr una aplicacion web tenias que:
1. Comprar un servidor fisico (un gabinete con CPU, RAM, discos)
2. Ponerlo en un data center (renta de espacio)
3. Configurar el sistema operativo, firewall, red
4. Instalar tu aplicacion
5. Mantener todo eso funcionando (actualizar, reparar, monitorear)

**Problemas de este modelo:**
- Caro y lento: comprar un servidor toma semanas
- Inflexible: si tu app necesita mas potencia, compras otro servidor fisico
- Riesgoso: si el data center tiene un problema, tu app se cae
- Desperdicio: si tu app usa el 10% del servidor, el otro 90% no se usa pero lo pagaste

**La nube resuelve esto:**
- Un proveedor (AWS, Azure, Google Cloud) ya compro millones de servidores
- Alquilas la capacidad que necesitas por hora o por segundo
- Si necesitas mas capacidad, la pides con un comando y la tienes en minutos
- Si algo falla, el proveedor lo reemplaza sin que tu lo notes
- Pagas solo lo que usas

**Por que importa para tu carrera:** hoy casi todas las empresas tech despliegan en la nube. No saber cloud es como no saber git: te descarta automaticamente en muchas entrevistas.

## Que es AWS

AWS (Amazon Web Services) es el proveedor de nube mas grande del mundo. Tiene mas de 200 servicios diferentes. Los dividimos en categorias:

| Categoria | Que ofrece | Ejemplos |
|-----------|------------|----------|
| **Compute** | Correr codigo | EC2, Lambda, ECS, EKS |
| **Storage** | Guardar archivos | S3, EBS, EFS |
| **Database** | Bases de datos manejadas | RDS, DynamoDB, Aurora |
| **Networking** | Conectar servicios | VPC, ALB, CloudFront |
| **Security** | Control de acceso | IAM, Secrets Manager, WAF |
| **Messaging** | Comunicacion async | SQS, SNS, EventBridge, Kinesis |
| **Observability** | Monitoreo y logs | CloudWatch, X-Ray |
| **Developer tools** | CI/CD | CodeBuild, CodePipeline |

No necesitas conocer los 200. En este plan vas a usar unos 15 servicios clave que cubren el 80% de lo que se pregunta en entrevistas.

## Glosario de conceptos clave

Lee esto antes de empezar. Cuando veas estos terminos en las fases, ya sabras que significan.

### Region
Una ubicacion geografica donde AWS tiene data centers. Ejemplos: `us-east-1` (Virginia), `sa-east-1` (Sao Paulo), `us-west-2` (Oregon). Elegis una region y todos tus recursos viven alli. Latencia mas baja si eliges una cercana a tus usuarios.

### Availability Zone (AZ)
Dentro de una region hay varios "sub-data-centers" fisicamente separados (diferentes edificios, energia, red). Se llaman AZ. Ejemplo: `us-east-1a`, `us-east-1b`, `us-east-1c`. Si un AZ se cae, los otros siguen funcionando. **Desplegar en multiples AZ es la clave de alta disponibilidad.**

### VPC (Virtual Private Cloud)
Tu red privada dentro de AWS. Nada entra ni sale sin tu permiso. Dentro de la VPC defines subnets, reglas de firewall, rutas.

### Subnet
Una subdivision de tu VPC. Cada subnet vive en un AZ especifico. Hay dos tipos:
- **Publica:** accesible desde internet (ejemplo: un load balancer)
- **Privada:** solo accesible desde dentro de la VPC (ejemplo: una base de datos)

### Internet Gateway
La puerta que conecta tu VPC con internet. Sin esto, nada entra ni sale.

### NAT Gateway
Permite que recursos en subnets privadas (como tus apps) inicien conexiones salientes a internet (para descargar dependencias, llamar APIs externas) sin ser accesibles desde afuera. Es como el router de tu casa.

### Security Group
Firewall a nivel de recurso. Dice "esta instancia acepta trafico en el puerto 8080 desde el load balancer". Es stateful: si permitis un request entrante, la respuesta sale automaticamente.

### Container
Una forma de empaquetar una aplicacion con todas sus dependencias en un "paquete" portable. Es como una mini maquina virtual pero mucho mas liviana. Docker es la herramienta mas popular para crear containers.

### Container Image
El "paquete" en si. Es un archivo (en realidad varias capas) que contiene tu app y todo lo que necesita para correr. Las imagenes se guardan en registries.

### Container Registry
Un repositorio donde guardas tus container images. Como GitHub pero para containers. AWS tiene uno llamado **ECR (Elastic Container Registry)**.

### Orchestrator (Orquestador)
Un sistema que corre tus containers por ti: los inicia, los reinicia si fallan, los escala, los balancea. Los dos mas populares son:
- **Kubernetes (K8s):** el estandar de la industria, complejo pero muy potente
- **ECS (AWS):** mas simple, especifico de AWS

### Fargate
Una opcion de ECS (y tambien de EKS) donde **no tenes que manejar los servidores subyacentes**. Le das tu container y AWS se encarga de todo lo demas. Pagas por CPU y memoria consumidos.

### Load Balancer
Un servicio que reparte requests entre multiples instancias de tu app. Si tenes 3 copias de property-api corriendo, el load balancer decide a cual mandar cada request. En AWS: **ALB (Application Load Balancer)** para HTTP/HTTPS.

### IAM (Identity and Access Management)
El sistema de control de acceso de AWS. Define **quien** (user o role) puede hacer **que** (accion) en **que recurso**. Todo en AWS pasa por IAM.

### IAM Role
Una identidad temporal que asume un servicio para hacer algo. Ejemplo: tu Lambda "asume" un role para leer de S3. El role tiene permisos definidos por policies.

### IAM Policy
Un documento JSON que lista permisos: "esta identidad puede hacer X accion en Y recurso". Se adjuntan a users, groups, o roles.

### Secrets Manager
Servicio para guardar secretos (passwords, API keys, tokens) de forma cifrada. Tu app los lee en runtime, nunca los commiteas al codigo.

### Infrastructure as Code (IaC)
Definir tu infraestructura (VPCs, databases, containers) en archivos de codigo en vez de hacer clicks en la consola. Ventajas:
- Reproducible: puedes crear un ambiente identico con un comando
- Versionable: esta en git, ves el historial de cambios
- Revisable: otros pueden revisar los cambios antes de aplicarlos
- Las dos herramientas principales: **Terraform** (open source, multi-cloud) y **CloudFormation** (solo AWS)

### CI/CD (Continuous Integration / Continuous Deployment)
- **CI:** cada vez que hacen push, el codigo se compila, testea automaticamente
- **CD:** si los tests pasan, se despliega automaticamente a un ambiente (staging, prod)
- Herramientas: **GitHub Actions**, GitLab CI, Jenkins, CircleCI

---

# Parte 2: Arquitectura objetivo

Esto es lo que vamos a construir al final de todas las fases. No te asustes si no entiendes todo ahora, cada pieza la cubrimos en una fase.

```
                                  INTERNET
                                      |
                                      v
                        +-------------------------+
                        |      CloudFront          |  (Fase 9)
                        |  (CDN global + cache)    |
                        +-------------+-----------+
                                      |
                                      v
                        +-------------------------+
                        |         WAF              |  (Fase 9)
                        |   (firewall de apps)     |
                        +-------------+-----------+
                                      |
                                      v
                        +-------------------------+
                        |    ALB (Load Balancer)   |  (Fase 6)
                        |    + HTTPS con ACM       |
                        +-------------+-----------+
                                      |
               +----------------------+-------------------+
               |                      |                    |
               v                      v                    v
     +-------------------+  +-------------------+  +-------------------+
     |   api-gateway     |  |   property-api    |  |   pricing-api     |
     |   ECS Fargate     |  |   ECS Fargate     |  |   ECS Fargate     |
     |   (Fase 4)        |  |   (Fase 4)        |  |   (Fase 4)        |
     +---------+---------+  +---------+---------+  +---------+---------+
               |                      |                      |
               |                      v                      |
               |            +-------------------+             |
               |            |  RDS PostgreSQL   |  (Fase 5)   |
               |            +-------------------+             |
               |                      |                      |
               |                      v                      |
               |            +-------------------+             |
               |            |    DynamoDB       |  (Fase 5b)  |
               |            +-------------------+             |
               |                                             |
               +--> Secrets Manager (Fase 5) <---------------+
               |                                             |
               v                                             v
     +-------------------+                         +-------------------+
     |   SQS / SNS       |  (Fase 8)               |  Kinesis Streams  |  (Fase 8b)
     +---------+---------+                         +---------+---------+
               |                                             |
               v                                             v
     +-------------------+                         +-------------------+
     |     Lambda        |                         |     Lambda        |
     +-------------------+                         +-------------------+

  Todo esto vive dentro de una VPC (Fase 2) con subnets publicas y privadas.
  Todo se monitorea con CloudWatch (Fase 7).
  Todo se define en Terraform (Fase 10).
  Todo se despliega via GitHub Actions (Fase 11).
```

---

# Parte 3: Fases

## Fase 0: Preparacion

### Objetivo
Tener todas las herramientas instaladas y una cuenta AWS lista antes de tocar nada.

### Conceptos nuevos
- **AWS Free Tier:** AWS da 12 meses gratis de muchos servicios para cuentas nuevas. Dentro de ciertos limites, no pagas nada. Despues de 12 meses o si superas los limites, cobran.
- **Billing Alert:** una alarma que te avisa si tu factura supera cierto monto. Obligatorio configurar esto antes de hacer nada.

### Pasos

1. **Crear cuenta AWS** en https://aws.amazon.com
   - Te pide tarjeta de credito (para validar, no cobra si te quedas en Free Tier)
   - Elegi la region `us-east-1` (mas barata y tiene todos los servicios)

2. **Configurar MFA (Multi-Factor Authentication) en el usuario root**
   - Usa Google Authenticator o similar
   - Critico: si alguien hackea tu cuenta, te pueden generar facturas de miles de dolares

3. **Configurar billing alerts**
   - Ve a Billing > Budgets
   - Crea un presupuesto de 20 USD/mes con alertas al 50%, 80%, 100%
   - Te llegan emails si te acercas al limite

4. **Instalar AWS CLI**
   ```bash
   # macOS
   brew install awscli

   # Verificar
   aws --version
   ```

5. **Instalar otras herramientas (las usaremos en fases posteriores)**
   ```bash
   brew install terraform       # Fase 10
   brew install jq              # procesar JSON
   brew install --cask docker   # ya lo tienes probablemente
   ```

### Criterio de exito
- Podes loggearte a la consola AWS con MFA
- Billing alerts configurados
- `aws --version` funciona en tu terminal

### Preguntas de entrevista
- **Por que configurar billing alerts desde el dia 1?**
  Para evitar sorpresas. Un bug o una mala configuracion puede generar miles de dolares en horas. Las alarmas te avisan antes.

- **Por que MFA en el usuario root?**
  El usuario root tiene todos los permisos. Si lo hackean, pueden crear recursos, borrar todo, generar facturas enormes.

### Duracion estimada
1 hora.

---

## Fase 1: IAM y cuenta AWS

### Objetivo
Entender como AWS controla quien puede hacer que. Crear un usuario IAM (no root) para trabajar dia a dia, y un role para que tus apps accedan a AWS.

### Conceptos nuevos

#### Usuario root vs usuario IAM
- **Root:** es el email con el que creaste la cuenta. Tiene TODOS los permisos. **NUNCA** lo uses dia a dia. Solo para configuracion inicial.
- **IAM user:** un usuario creado adentro de la cuenta. Tiene solo los permisos que le des.

**Analogia:** el root es el dueno del edificio. Los IAM users son los inquilinos, cada uno con llaves de sus propios departamentos.

#### IAM User vs IAM Role
- **User:** una identidad permanente. Tiene credenciales (access key + secret) que vos usas.
- **Role:** una identidad temporal que alguien (o algo) "asume" para hacer una tarea especifica.

**Ejemplo:** tu property-api corriendo en ECS necesita leer de una tabla DynamoDB. No le das credenciales permanentes. Le das un **role** que dice "este container puede leer DynamoDB". Cuando el container arranca, "asume" el role automaticamente.

#### Policy (politica)
Documento JSON que lista permisos. Ejemplo:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::mi-bucket/*"
    }
  ]
}
```
Dice: "permite la accion `s3:GetObject` en todos los objetos del bucket `mi-bucket`".

#### ARN (Amazon Resource Name)
Identificador unico de cada recurso en AWS. Formato:
```
arn:aws:<servicio>:<region>:<cuenta>:<recurso>
```
Ejemplo: `arn:aws:s3:::mi-bucket` (los buckets S3 no tienen region ni cuenta en su ARN, por eso los `::` vacios).

#### Principle of Least Privilege
Dar solo los permisos minimos necesarios. Nunca dar `Admin` a un servicio. Si tu Lambda solo lee de una tabla, dale permiso solo para leer esa tabla, nada mas.

### Pasos

1. **Crear un IAM user para vos**
   - Consola > IAM > Users > Create user
   - Nombre: `mauricio-dev`
   - Habilita "Programmatic access" y "Management Console access"
   - Attach policy: `AdministratorAccess` (solo para este proyecto, en empresas reales seria mas restrictivo)

2. **Generar access keys**
   - En tu user > Security credentials > Create access key
   - Guardalas en un lugar seguro (las vas a usar en `aws configure`)

3. **Configurar AWS CLI**
   ```bash
   aws configure
   # AWS Access Key ID: <la que acabas de crear>
   # AWS Secret Access Key: <la que acabas de crear>
   # Default region: us-east-1
   # Default output format: json
   ```

4. **Verificar**
   ```bash
   aws sts get-caller-identity
   # Deberia mostrar tu user ARN
   ```

5. **Cerrar sesion del root y usar solo el user IAM desde ahora**

### Criterio de exito
- Tenes un user IAM con access keys configuradas
- `aws sts get-caller-identity` funciona
- Ya no usas el root

### Preguntas de entrevista
- **Diferencia entre IAM user y IAM role?**
  User = identidad permanente con credenciales fijas. Role = identidad temporal que se "asume" para una tarea especifica. Se usan credenciales temporales rotadas automaticamente.

- **Que es el principle of least privilege?**
  Dar solo los permisos minimos necesarios para que algo funcione. Reduce el "blast radius" si hay un problema de seguridad.

- **Como darias acceso a una app corriendo en ECS para leer de S3?**
  Creo un IAM role con una policy que permita `s3:GetObject` en el bucket especifico. Se lo asigno al task definition de ECS. El container asume el role automaticamente al arrancar via IMDS (Instance Metadata Service).

### Duracion estimada
1-2 horas.

---

## Fase 2: VPC y networking

### Objetivo
Crear la red privada donde vivira tu aplicacion. Entender subnets publicas y privadas, security groups, y como se comunican los servicios.

### Conceptos nuevos

#### CIDR block
Un rango de IPs. Ejemplo: `10.0.0.0/16` significa "todas las IPs que empiezan con `10.0.`" (65536 IPs). El `/16` indica cuantos bits son fijos.

Rangos comunes para VPCs privadas:
- `10.0.0.0/8` (16M IPs, excesivo)
- `10.0.0.0/16` (65k IPs, estandar)
- `172.16.0.0/12` o `192.168.0.0/16` (alternativas)

#### Subnet
Una subdivision del CIDR de la VPC. Ejemplo:
- VPC: `10.0.0.0/16`
- Subnet publica AZ-a: `10.0.1.0/24` (256 IPs)
- Subnet publica AZ-b: `10.0.2.0/24`
- Subnet privada AZ-a: `10.0.11.0/24`
- Subnet privada AZ-b: `10.0.12.0/24`

**Regla de oro:** cada tipo de recurso va en el tipo correcto de subnet.

| Recurso | Subnet publica | Subnet privada |
|---------|----------------|----------------|
| Load Balancer (ALB) | ✓ | |
| NAT Gateway | ✓ | |
| Tus apps (ECS tasks) | | ✓ |
| Base de datos (RDS) | | ✓ |

#### Internet Gateway (IGW)
Conecta la VPC a internet. Se adjunta a la VPC. Las subnets **publicas** tienen una ruta hacia el IGW.

#### NAT Gateway
Permite trafico saliente a internet desde subnets privadas (para que tus apps puedan descargar dependencias, llamar APIs externas) sin que nadie pueda iniciar conexiones entrantes.

**Problema:** el NAT Gateway cuesta aprox 32 USD/mes por AZ. Es el gasto mas grande de este proyecto. Para minimizar costos usamos **1 solo** NAT Gateway compartido (aunque en produccion real querrias uno por AZ).

#### Route Table
Las reglas de ruteo de una subnet. Ejemplo:
- Subnet publica: "trafico a 0.0.0.0/0 va al IGW"
- Subnet privada: "trafico a 0.0.0.0/0 va al NAT Gateway"

#### Security Group
Firewall de una instancia. Reglas tipo: "acepta trafico en puerto 8080 desde el SG del load balancer".

**Diferencia con NACL (Network ACL):**
- Security Group: stateful, a nivel de instancia, solo reglas de allow
- NACL: stateless, a nivel de subnet, tiene allow y deny

En la practica usas Security Groups casi siempre.

### Diseno de nuestra VPC

```
VPC: 10.0.0.0/16 (us-east-1)
|
+-- Subnet publica AZ-a (10.0.1.0/24)
|     |
|     +-- ALB
|     +-- NAT Gateway
|
+-- Subnet publica AZ-b (10.0.2.0/24)
|     |
|     +-- ALB (alta disponibilidad)
|
+-- Subnet privada AZ-a (10.0.11.0/24)
|     |
|     +-- ECS tasks (apps)
|     +-- RDS PostgreSQL (primary)
|
+-- Subnet privada AZ-b (10.0.12.0/24)
      |
      +-- ECS tasks (apps)
      +-- RDS PostgreSQL (standby)
```

### Pasos

1. **Crear VPC via consola** (en fases posteriores haremos esto con Terraform)
   - VPC > Create VPC
   - Usa "VPC and more" wizard (crea VPC + subnets + IGW + route tables automaticamente)
   - CIDR: `10.0.0.0/16`
   - 2 AZs
   - 2 subnets publicas, 2 privadas
   - 1 NAT Gateway (para minimizar costos)

2. **Verificar recursos creados**
   ```bash
   aws ec2 describe-vpcs
   aws ec2 describe-subnets
   aws ec2 describe-internet-gateways
   aws ec2 describe-nat-gateways
   ```

3. **Crear Security Groups basicos**
   - `alb-sg`: permite puerto 80 y 443 desde internet (`0.0.0.0/0`)
   - `ecs-sg`: permite puerto 8080 desde `alb-sg`
   - `rds-sg`: permite puerto 5432 desde `ecs-sg`

### Criterio de exito
- VPC creada con 4 subnets (2 publicas, 2 privadas)
- IGW y NAT Gateway configurados
- Security Groups creados con reglas correctas
- Podes ver todo en la consola

### Preguntas de entrevista
- **Diferencia entre subnet publica y privada?**
  Publica tiene ruta al IGW (accesible desde internet). Privada no tiene IGW, solo NAT (puede salir pero nadie puede entrar).

- **Diferencia entre Security Group y NACL?**
  SG: stateful, a nivel de instancia, solo reglas allow. NACL: stateless, a nivel de subnet, allow y deny.

- **Por que desplegar en multiples AZ?**
  Alta disponibilidad. Si un AZ se cae (incendio, falla de red), los recursos en el otro AZ siguen funcionando.

- **Por que poner RDS en subnet privada?**
  Seguridad. Las bases de datos nunca deben ser accesibles directamente desde internet. Solo desde la VPC.

- **Cuanto cuesta un NAT Gateway?**
  Aprox 32 USD/mes por AZ + trafico procesado. Es uno de los costos mas grandes en arquitecturas chicas. Alternativa mas barata: NAT Instance (EC2 con NAT) pero es manual de mantener.

### Duracion estimada
2-3 horas.

---

## Fase 3: Docker y ECR

### Objetivo
Crear imagenes Docker production-grade de tus 3 apps y subirlas a ECR (el registry de AWS).

### Conceptos nuevos

#### Por que Docker?
Tu app Spring Boot necesita: Java 21, las dependencias Maven, tu codigo, configuracion. Instalar todo esto en cada servidor donde queres correrla es:
- Lento
- Inconsistente (distintas versiones en distintos ambientes)
- Propenso a errores

Docker empaqueta todo esto en una **imagen** portable. La imagen corre igual en tu laptop, en staging, y en produccion.

#### Dockerfile
Archivo de texto con las instrucciones para construir una imagen:
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### Multi-stage build
Tecnica para hacer imagenes mas chicas. Tenes una etapa de build (con Maven, JDK completo, todos los archivos fuente) y una etapa final (solo con el JAR y JRE). La imagen final no tiene el codigo fuente ni Maven, solo lo necesario para correr.

#### Container Registry
Un almacen de imagenes Docker. Tu haces `docker push mi-imagen` al registry, y en produccion haces `docker pull mi-imagen`. Opciones:
- Docker Hub (publico, el que usa `docker run nginx`)
- GitHub Container Registry
- **AWS ECR** (Elastic Container Registry) — lo usaremos

#### ECR (Elastic Container Registry)
Registry de AWS. Ventajas:
- Integrado con IAM (permisos finos)
- Privado por defecto
- Cerca de tu infra (push y pull rapidos)
- Scanning de vulnerabilidades automatico

### Pasos

1. **Mejorar tus Dockerfiles existentes** (probablemente ya los tenes para docker-compose)

   Ejemplo para property-api (production-grade):
   ```dockerfile
   # --- Stage 1: build ---
   FROM maven:3.9-eclipse-temurin-21 AS build
   WORKDIR /build
   COPY pom.xml .
   COPY property-api/pom.xml property-api/
   COPY pricing-api-contract/pom.xml pricing-api-contract/
   # Descargar dependencias (se cachea si no cambia el pom)
   RUN mvn -f property-api/pom.xml dependency:go-offline -B

   # Copiar codigo y compilar
   COPY pricing-api-contract pricing-api-contract
   COPY property-api property-api
   RUN mvn -f property-api/pom.xml clean package -DskipTests

   # --- Stage 2: runtime ---
   FROM eclipse-temurin:21-jre-alpine
   # Usuario no-root (seguridad)
   RUN addgroup -S app && adduser -S app -G app
   USER app

   WORKDIR /app
   COPY --from=build /build/property-api/target/*.jar app.jar

   EXPOSE 8081
   # Healthcheck para que ECS sepa si esta vivo
   HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
     CMD wget --quiet --tries=1 --spider http://localhost:8081/actuator/health || exit 1

   ENTRYPOINT ["java", "-XX:+UseZGC", "-jar", "app.jar"]
   ```

2. **Agregar Spring Actuator a cada servicio**

   En el pom.xml:
   ```xml
   <dependency>
     <groupId>org.springframework.boot</groupId>
     <artifactId>spring-boot-starter-actuator</artifactId>
   </dependency>
   ```

   En application.yml:
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health,info,metrics
     endpoint:
       health:
         show-details: when-authorized
   ```

3. **Crear repositorios en ECR**
   ```bash
   aws ecr create-repository --repository-name property-api
   aws ecr create-repository --repository-name pricing-api
   aws ecr create-repository --repository-name api-gateway
   ```

4. **Autenticar Docker con ECR**
   ```bash
   aws ecr get-login-password --region us-east-1 | \
     docker login --username AWS --password-stdin \
     <tu-cuenta>.dkr.ecr.us-east-1.amazonaws.com
   ```

5. **Build y push**
   ```bash
   # Build
   docker build -t property-api:v1 -f property-api/Dockerfile .

   # Tag con el URI completo de ECR
   docker tag property-api:v1 \
     <tu-cuenta>.dkr.ecr.us-east-1.amazonaws.com/property-api:v1

   # Push
   docker push <tu-cuenta>.dkr.ecr.us-east-1.amazonaws.com/property-api:v1
   ```

   Repetir para pricing-api y api-gateway.

### Criterio de exito
- Las 3 imagenes estan en ECR (se ven en la consola)
- Cada imagen tiene multi-stage build
- Cada imagen corre como usuario no-root
- Cada imagen tiene healthcheck definido

### Preguntas de entrevista
- **Que es un multi-stage build y por que usarlo?**
  Es tener multiples etapas en un Dockerfile. Una etapa con todas las herramientas de build (JDK, Maven, fuentes), otra final con solo el artefacto y runtime. La imagen final es mucho mas chica y no contiene el codigo fuente (seguridad y tamano).

- **Por que correr containers como usuario no-root?**
  Si hay una vulnerabilidad que permite ejecutar comandos dentro del container, un atacante con usuario root tiene mas potencial de dano (especialmente si rompe el aislamiento del container). Correr como non-root limita el blast radius.

- **Diferencia entre layers de Docker?**
  Cada linea del Dockerfile que modifica el filesystem crea una layer. Docker cachea layers: si no cambia una instruccion, no la re-ejecuta. Por eso ponemos `COPY pom.xml` antes de `COPY src` — si solo cambia el codigo, no re-descarga dependencias.

- **Como reduces el tamano de una imagen?**
  Multi-stage build, base images alpine/distroless, limpiar caches (`rm -rf /var/cache/apt`), combinar RUN commands, .dockerignore.

### Duracion estimada
3-4 horas (incluye mejorar Dockerfiles existentes).

---

## Fase 4: ECS Fargate

### Objetivo
Correr tus 3 containers en AWS sin manejar servidores. Entender task definitions, services, y como ECS orquesta containers.

### Conceptos nuevos

#### ECS (Elastic Container Service)
El orquestador de containers de AWS. Dos modos:
- **EC2 launch type:** tu manejas los servidores (EC2 instances) donde corren los containers
- **Fargate:** AWS maneja los servidores por vos. Solo le das el container. **Usaremos este.**

#### Cluster
Un grupo logico donde corren tus tasks. Un cluster puede tener multiples services.

#### Task Definition
Una "receta" que define como correr tu container:
- Que imagen usar (URI de ECR)
- Cuanta CPU y memoria
- Puerto que expone
- Variables de entorno
- Role IAM que asume
- Logs adonde van

Es como un "docker run" expresado como JSON.

#### Task
Una instancia corriendo de una task definition. Si tu service tiene `desiredCount=3`, hay 3 tasks.

#### Service
La "definicion" de como correr tu app de forma permanente:
- Cuantas tasks quiero corriendo (`desiredCount`)
- A que task definition se refieren
- Que load balancer usar
- Estrategia de deploy (rolling update, blue/green)

**Ecuacion mental:** Service + Task Definition = tu app corriendo en produccion.

#### Capacity Provider
Define donde corren las tasks. Para Fargate: `FARGATE` y `FARGATE_SPOT` (mas barato pero puede interrumpirse).

### Arquitectura detallada (ECS)

```
Cluster: "java-training-cluster"
  |
  +-- Service: "property-api-service"
  |     |
  |     +-- Task Definition: "property-api:v1"
  |     |     - Image: ECR/property-api:v1
  |     |     - CPU: 512 (0.5 vCPU)
  |     |     - Memory: 1024 MB
  |     |     - Port: 8081
  |     |     - Role: property-api-task-role
  |     |
  |     +-- desiredCount: 2 (2 tasks corriendo)
  |     +-- Load balancer: ALB target group "property-api-tg"
  |     +-- Subnets: privadas AZ-a y AZ-b
  |
  +-- Service: "pricing-api-service"  (similar estructura)
  +-- Service: "api-gateway-service"  (similar estructura)
```

### Pasos

1. **Crear cluster**
   ```bash
   aws ecs create-cluster --cluster-name java-training-cluster
   ```

2. **Crear IAM role para tasks**
   - **Execution role:** permite a ECS hacer cosas por vos (pullear imagen de ECR, escribir logs)
   - **Task role:** permisos de tu app en AWS (ej: acceder a DynamoDB)

3. **Crear task definition** (JSON)
   ```json
   {
     "family": "property-api",
     "networkMode": "awsvpc",
     "requiresCompatibilities": ["FARGATE"],
     "cpu": "512",
     "memory": "1024",
     "executionRoleArn": "arn:aws:iam::xxx:role/ecsTaskExecutionRole",
     "taskRoleArn": "arn:aws:iam::xxx:role/property-api-task-role",
     "containerDefinitions": [
       {
         "name": "property-api",
         "image": "xxx.dkr.ecr.us-east-1.amazonaws.com/property-api:v1",
         "portMappings": [{"containerPort": 8081}],
         "environment": [
           {"name": "SPRING_PROFILES_ACTIVE", "value": "aws"}
         ],
         "logConfiguration": {
           "logDriver": "awslogs",
           "options": {
             "awslogs-group": "/ecs/property-api",
             "awslogs-region": "us-east-1",
             "awslogs-stream-prefix": "ecs"
           }
         }
       }
     ]
   }
   ```

4. **Crear CloudWatch log group**
   ```bash
   aws logs create-log-group --log-group-name /ecs/property-api
   ```

5. **Registrar task definition**
   ```bash
   aws ecs register-task-definition --cli-input-json file://task-def.json
   ```

6. **Crear service** (sin load balancer por ahora, lo agregamos en Fase 6)
   ```bash
   aws ecs create-service \
     --cluster java-training-cluster \
     --service-name property-api-service \
     --task-definition property-api \
     --desired-count 2 \
     --launch-type FARGATE \
     --network-configuration "awsvpcConfiguration={subnets=[subnet-xxx,subnet-yyy],securityGroups=[sg-xxx]}"
   ```

7. **Verificar que las tasks esten corriendo**
   ```bash
   aws ecs list-tasks --cluster java-training-cluster
   aws ecs describe-tasks --cluster java-training-cluster --tasks <task-id>
   ```

### Criterio de exito
- Los 3 services corriendo en el cluster
- `desiredCount` = `runningCount` para cada service
- Los logs llegan a CloudWatch
- Podes ver los containers en la consola

### Preguntas de entrevista
- **Diferencia entre ECS y EKS?**
  ECS es proprietary de AWS, mas simple. EKS es Kubernetes manejado, mas potente pero complejo. Si solo estas en AWS y queres simplicidad: ECS. Si queres portabilidad o features avanzados de k8s: EKS.

- **Diferencia entre Fargate y EC2 launch type?**
  Fargate: serverless, AWS maneja los servidores, pagas por CPU/memory. EC2: vos manejas los servidores, pagas por las instancias (corran o no containers).

- **Diferencia entre task y service?**
  Task es una instancia corriendo de una task definition. Service mantiene N tasks corriendo siempre (si una muere, lanza otra).

- **Que es un task execution role vs task role?**
  Execution role: usado por el agente ECS para pullear imagenes y escribir logs. Task role: usado por TU aplicacion corriendo en el container (ej: acceder a DynamoDB).

- **Como haces rolling updates con ECS?**
  En el service tenes `minimumHealthyPercent` y `maximumPercent`. ECS lanza tasks nuevas, espera que esten healthy, y para las viejas. Zero-downtime deploy.

### Duracion estimada
4-5 horas.

---

## Fase 5: RDS + Flyway + Secrets Manager

### Objetivo
Migrar de PostgreSQL local (Docker) a PostgreSQL manejado por AWS. Usar Flyway para versionar el schema. Usar Secrets Manager para credenciales.

### Conceptos nuevos

#### RDS (Relational Database Service)
Base de datos manejada por AWS. Soporta PostgreSQL, MySQL, MariaDB, Oracle, SQL Server, Aurora.

**Que hace AWS por vos:**
- Provisioning y patches del OS
- Backups automaticos diarios
- Replica standby en otro AZ (Multi-AZ)
- Monitoring
- Point-in-time recovery (recuperar a cualquier momento en los ultimos N dias)

**Que seguis haciendo vos:**
- Disenar el schema
- Escribir queries
- Optimizar performance (indices, query plans)

#### Multi-AZ vs Read Replica
- **Multi-AZ:** standby sincronico en otro AZ. Si el primary cae, failover automatico. Para alta disponibilidad, no para escalar lecturas.
- **Read Replica:** copia asincronica. Podes leer de ella (distribuir carga). No es failover automatico.

#### Flyway
Herramienta para versionar cambios de schema. Mantenes archivos tipo:
```
db/migration/
  V1__create_properties_table.sql
  V2__add_bedrooms_column.sql
  V3__create_projects_table.sql
```

Flyway:
- Se ejecuta al arrancar la app
- Crea una tabla `flyway_schema_history` que registra que migraciones ya corrieron
- Aplica las nuevas en orden
- Imposible saltearse una o correr en desorden

**Por que esto importa:** en produccion, nunca haces `ALTER TABLE` manualmente. Siempre via migracion versionada. Asi cada ambiente (dev, staging, prod) tiene el mismo schema.

#### Secrets Manager
Servicio para guardar secretos de forma cifrada. Tu app los lee en runtime.

**Flujo:**
1. Creas el secret: `prod/rds/property-api` con valor `{"username":"admin","password":"xxx"}`
2. Le das permiso IAM a tu task role para leer ese secret
3. Tu app lee el secret en el arranque usando AWS SDK o via ECS secrets injection

**Ventajas:**
- Rotacion automatica (AWS cambia el password periodicamente)
- Audit log (quien leyo el secret y cuando)
- Versioning
- Cifrado con KMS

### Pasos

1. **Crear DB subnet group** (RDS necesita saber en que subnets privadas vive)
   ```bash
   aws rds create-db-subnet-group \
     --db-subnet-group-name java-training-db-subnet \
     --subnet-ids subnet-xxx subnet-yyy \
     --db-subnet-group-description "Subnets privadas para RDS"
   ```

2. **Crear Secret en Secrets Manager**
   ```bash
   aws secretsmanager create-secret \
     --name prod/rds/property-api \
     --secret-string '{"username":"dbadmin","password":"GeneraUnoFuerte123!"}'
   ```

3. **Crear instancia RDS**
   ```bash
   aws rds create-db-instance \
     --db-instance-identifier java-training-db \
     --db-instance-class db.t3.micro \
     --engine postgres \
     --engine-version 16.1 \
     --allocated-storage 20 \
     --master-username dbadmin \
     --master-user-password GeneraUnoFuerte123! \
     --vpc-security-group-ids sg-rds-xxx \
     --db-subnet-group-name java-training-db-subnet \
     --multi-az \
     --storage-encrypted \
     --backup-retention-period 7
   ```
   (En produccion pondrias password via Secrets Manager con `--manage-master-user-password`.)

4. **Agregar Flyway a property-api**

   `pom.xml`:
   ```xml
   <dependency>
     <groupId>org.flywaydb</groupId>
     <artifactId>flyway-core</artifactId>
   </dependency>
   <dependency>
     <groupId>org.flywaydb</groupId>
     <artifactId>flyway-database-postgresql</artifactId>
   </dependency>
   ```

   Crear migraciones en `src/main/resources/db/migration/`:

   `V1__create_initial_schema.sql`:
   ```sql
   CREATE TABLE properties (
     id UUID PRIMARY KEY,
     name VARCHAR(255) NOT NULL,
     city VARCHAR(100) NOT NULL,
     price DECIMAL(12,2) NOT NULL,
     bedrooms INT,
     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   );

   CREATE TABLE projects (
     id UUID PRIMARY KEY,
     name VARCHAR(255) NOT NULL,
     description TEXT
   );

   ALTER TABLE properties ADD COLUMN project_id UUID REFERENCES projects(id);

   CREATE TABLE users (
     id UUID PRIMARY KEY,
     email VARCHAR(255) UNIQUE NOT NULL,
     password VARCHAR(255) NOT NULL,
     role VARCHAR(20) NOT NULL
   );
   ```

   En `application-aws.yml`:
   ```yaml
   spring:
     jpa:
       hibernate:
         ddl-auto: validate  # ya no "create-drop", Flyway maneja el schema
     flyway:
       enabled: true
   ```

5. **Configurar la app para leer secrets**

   Opcion A (mas simple): ECS inyecta el secret como environment variable
   ```json
   "secrets": [
     {
       "name": "DB_PASSWORD",
       "valueFrom": "arn:aws:secretsmanager:...:prod/rds/property-api:password::"
     }
   ]
   ```

   Opcion B: AWS SDK en el arranque de Spring. Mas flexible, mas complejo.

6. **Actualizar task definition** con las envs de conexion a RDS.

7. **Re-desplegar los services y verificar que se conecten**

### Criterio de exito
- RDS corriendo en subnet privada
- Tu property-api conecta a RDS (no a PostgreSQL local)
- Flyway aplico las migraciones al arrancar
- El password no esta en el codigo, viene de Secrets Manager
- `SELECT * FROM flyway_schema_history` muestra las migraciones aplicadas

### Preguntas de entrevista
- **Por que RDS y no Postgres en un container?**
  RDS provee: backups automaticos, Multi-AZ failover, patches, monitoring, point-in-time recovery. Todo eso es costoso de implementar y mantener por tu cuenta. Para production DB, RDS es el estandar.

- **Como manejas migraciones de schema en produccion?**
  Flyway (o Liquibase). Cada cambio es un archivo versionado. Al deploy, Flyway aplica las migraciones pendientes. Nunca se modifica schema manualmente. Rollback: se escribe una migracion inversa (no se modifica la anterior).

- **Como manejas secrets en produccion?**
  Nunca en el codigo ni en environment variables comiteadas. Uso Secrets Manager (AWS) o Parameter Store (mas barato para secrets simples). Las aplicaciones leen el secret en el arranque. Rotacion automatica cuando es posible.

- **Que es Multi-AZ en RDS?**
  Replica synchronica en otro Availability Zone. Si el primary falla, failover automatico en ~60-120 segundos. No es para escalar lecturas (para eso: read replicas).

- **Diferencia entre ddl-auto: validate y create-drop?**
  `validate`: Hibernate verifica que el schema exista y matchee las entidades, no modifica nada. `create-drop`: borra y recrea el schema en cada arranque. `validate` es lo correcto en produccion.

### Duracion estimada
4-5 horas.

---

## Fase 5b: DynamoDB

### Objetivo
Agregar DynamoDB como segundo datastore. Entender cuando usar NoSQL vs SQL. Practicar schema design de DynamoDB.

### Conceptos nuevos

#### Que es DynamoDB
Base de datos NoSQL de AWS. Key-value con soporte para documentos. Caracteristicas:
- **Fully managed:** no hay servidor, pagas por uso
- **Escalable:** soporta millones de requests por segundo
- **Latencia consistente:** < 10ms en la mayoria de operaciones
- **Multi-region replication**

#### Cuando usar DynamoDB vs PostgreSQL
| Caso | Mejor opcion |
|------|--------------|
| Transacciones ACID complejas con joins | PostgreSQL |
| Schema rigido, queries variables | PostgreSQL |
| Lookup por ID a altisima escala | DynamoDB |
| Datos semi-estructurados (documentos) | DynamoDB |
| Session storage, cache, idempotency keys | DynamoDB |
| Event sourcing, audit logs | DynamoDB |
| Reportes con GROUP BY, JOIN | PostgreSQL |

#### Tablas, items, attributes
- **Table:** conjunto de items. Como una tabla SQL pero sin schema rigido.
- **Item:** una fila. Es un diccionario de atributos.
- **Attribute:** un par key-value dentro de un item.

#### Primary key
Dos modelos:
- **Simple:** solo partition key (hash). Ejemplo: `userId`
- **Composite:** partition key + sort key. Ejemplo: `userId` + `timestamp`

**La partition key determina donde se guarda fisicamente el item.** Items con la misma partition key se guardan juntos, items con diferente se distribuyen.

#### Capacity modes
- **On-demand:** pagas por request. Auto-scale infinito. Mas caro por request pero sin pensar.
- **Provisioned:** reservas RCUs (read) y WCUs (write). Mas barato a escala pero tenes que estimar.

### Caso de uso en tu proyecto

Agreguemos DynamoDB para:
1. **Idempotency keys:** evitar que el mismo request se procese 2 veces (util en la Fase 8 con SQS/Lambda)
2. **Watermarks:** "ultima fecha procesada por el consumer X"
3. **Session storage:** como backup al JWT stateless, si necesitamos revocacion

Tabla ejemplo:
```
Table: IdempotencyKeys
Partition key: key (string)
Attributes:
  - key: "evt-abc-123"
  - created_at: 2026-04-22T14:30:00Z
  - ttl: 1697716800 (unix timestamp, 7 dias despues)
  - processed_by: "bronze-writer-lambda"
```

### Pasos

1. **Crear la tabla**
   ```bash
   aws dynamodb create-table \
     --table-name IdempotencyKeys \
     --attribute-definitions AttributeName=key,AttributeType=S \
     --key-schema AttributeName=key,KeyType=HASH \
     --billing-mode PAY_PER_REQUEST
   ```

2. **Habilitar TTL** (AWS borra items expirados automaticamente)
   ```bash
   aws dynamodb update-time-to-live \
     --table-name IdempotencyKeys \
     --time-to-live-specification Enabled=true,AttributeName=ttl
   ```

3. **Agregar AWS SDK a property-api**
   ```xml
   <dependency>
     <groupId>software.amazon.awssdk</groupId>
     <artifactId>dynamodb</artifactId>
   </dependency>
   <dependency>
     <groupId>software.amazon.awssdk</groupId>
     <artifactId>dynamodb-enhanced</artifactId>
   </dependency>
   ```

4. **Crear IAM policy para que el task role pueda acceder**
   ```json
   {
     "Effect": "Allow",
     "Action": ["dynamodb:GetItem","dynamodb:PutItem"],
     "Resource": "arn:aws:dynamodb:us-east-1:xxx:table/IdempotencyKeys"
   }
   ```

5. **Usar DynamoDB Enhanced Client en Spring**
   ```java
   @Bean
   public DynamoDbClient dynamoDbClient() {
       return DynamoDbClient.builder().region(Region.US_EAST_1).build();
   }

   @Bean
   public DynamoDbEnhancedClient enhancedClient(DynamoDbClient client) {
       return DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
   }
   ```

### Criterio de exito
- Tabla creada en DynamoDB
- Tu app puede leer/escribir items
- TTL configurado (items se borran solos)
- El IAM task role tiene solo permisos para esa tabla (least privilege)

### Preguntas de entrevista
- **Cuando usarias DynamoDB vs PostgreSQL?**
  DynamoDB: lookups por key a alta escala, latencia consistente, datos semi-estructurados, idempotency/sessions/cache. PostgreSQL: transacciones complejas, queries ad-hoc, reportes, relaciones.

- **Que es una partition key?**
  El atributo que DynamoDB usa para decidir en que fisica particion guardar el item. Determina la escalabilidad: items con la misma partition key se guardan juntos, items diferentes se distribuyen.

- **Que es un hot partition?**
  Cuando muchos requests van a la misma partition key. DynamoDB limita por particion (3000 RCUs / 1000 WCUs). Solucion: diseno de partition key que distribuya uniformemente (ej: no usar "timestamp" como PK, usar algo mas distribuido).

- **Que es eventual consistency en DynamoDB?**
  Por default, una lectura despues de una escritura puede no ver el ultimo valor (si lees de una replica que aun no se actualizo). Podes pedir "strong consistency" en la lectura, pero cuesta mas RCUs y es mas lento.

- **Como modelarias un feed de posts de usuarios en DynamoDB?**
  Single-table design. Partition key: userId. Sort key: "POST#<timestamp>". Permite query "todos los posts del usuario X ordenados por fecha" eficientemente.

### Duracion estimada
3 horas.

---

## Fase 6: ALB + HTTPS

### Objetivo
Exponer tu app a internet de forma segura. Load balancing entre multiples tasks. HTTPS con certificado gratis de AWS.

### Conceptos nuevos

#### Load Balancer
Distribuye trafico entrante entre multiples instancias. Beneficios:
- Alta disponibilidad (si una task muere, trafico va a otras)
- Escalabilidad (sumar tasks suma capacidad)
- Health checks (solo manda trafico a tasks sanas)
- Terminacion SSL (el LB hace HTTPS, las tasks hablan HTTP interno)

#### Tipos de Load Balancer en AWS

| Tipo | Capa OSI | Uso |
|------|----------|-----|
| **ALB (Application LB)** | 7 (HTTP/HTTPS) | Web apps, APIs REST/gRPC |
| **NLB (Network LB)** | 4 (TCP/UDP) | Altisimo throughput, TCP custom |
| **CLB (Classic LB)** | 4 y 7 | Legacy, no usar en proyectos nuevos |
| **GWLB (Gateway LB)** | 3 | Inspeccion de trafico (firewalls) |

Usaremos **ALB** para nuestras apps HTTP.

#### Componentes de un ALB

1. **Listener:** escucha en un puerto (80, 443). Define reglas de routing.
2. **Target Group:** un grupo de targets (tasks ECS, instancias EC2) que reciben trafico.
3. **Rule:** "si el path es `/api/*`, manda al target group X; si es `/rpc`, al Y"
4. **Health Check:** URL que el ALB chequea periodicamente para saber si cada target esta sano.

```
                ALB
                 |
         +-------+--------+
         |                |
    Listener :80    Listener :443
         |                |
         +-------+--------+
                 |
          Rule-based routing
                 |
         +-------+--------+
         |                |
  Path: /api/*      Path: /rpc
         |                |
         v                v
  property-api-tg   pricing-api-tg
   (2 tasks)         (2 tasks)
```

#### ACM (Certificate Manager)
Servicio de AWS que emite certificados SSL gratis. Se integran con ALB automaticamente. Renovacion automatica.

**Requisito:** necesitas un dominio. Opciones:
- Comprar uno en Route53 (~$12/año para `.com`)
- Usar uno que ya tengas
- Para practica, usar el DNS del ALB directamente (sin HTTPS) y luego agregar un dominio

### Pasos

1. **Crear Target Groups** (uno por servicio)
   ```bash
   aws elbv2 create-target-group \
     --name property-api-tg \
     --protocol HTTP \
     --port 8081 \
     --vpc-id vpc-xxx \
     --target-type ip \
     --health-check-path /actuator/health \
     --health-check-interval-seconds 30
   ```

2. **Crear ALB**
   ```bash
   aws elbv2 create-load-balancer \
     --name java-training-alb \
     --subnets subnet-pub-a subnet-pub-b \
     --security-groups sg-alb-xxx \
     --scheme internet-facing \
     --type application
   ```

3. **Obtener certificado en ACM** (si tenes dominio)
   ```bash
   aws acm request-certificate \
     --domain-name api.tu-dominio.com \
     --validation-method DNS
   ```
   Agregar los registros DNS que te da ACM para validar.

4. **Crear listener HTTP (80) → redirigir a HTTPS (443)**
   ```bash
   aws elbv2 create-listener \
     --load-balancer-arn alb-xxx \
     --protocol HTTP \
     --port 80 \
     --default-actions Type=redirect,RedirectConfig={Protocol=HTTPS,Port=443,StatusCode=HTTP_301}
   ```

5. **Crear listener HTTPS (443)**
   ```bash
   aws elbv2 create-listener \
     --load-balancer-arn alb-xxx \
     --protocol HTTPS \
     --port 443 \
     --certificates CertificateArn=cert-xxx \
     --default-actions Type=forward,TargetGroupArn=api-gateway-tg
   ```

6. **Agregar rules de routing** (si queres routear diferentes paths a diferentes services)
   ```bash
   aws elbv2 create-rule \
     --listener-arn listener-xxx \
     --priority 10 \
     --conditions Field=path-pattern,Values='/api/*' \
     --actions Type=forward,TargetGroupArn=property-api-tg
   ```

7. **Actualizar tus ECS services** para registrarse en los target groups
   (al crear o actualizar el service, especificas `loadBalancers`)

### Criterio de exito
- ALB tiene DNS publico (tipo `java-training-alb-xxx.us-east-1.elb.amazonaws.com`)
- HTTP redirige a HTTPS
- HTTPS funciona con certificado valido
- Podes hacer `curl https://<dns-alb>/api/properties` y funciona
- Las 3 apps tienen health checks pasando

### Preguntas de entrevista
- **Diferencia entre ALB y NLB?**
  ALB: capa 7, HTTP/HTTPS, path-based routing, WAF integration. NLB: capa 4, TCP/UDP, altisimo throughput, IPs fijas. Usa ALB para web apps, NLB para TCP custom.

- **Que es un health check y por que importa?**
  El LB verifica periodicamente (ej: cada 30s) que cada target responda. Si N consecutivos fallan, deja de mandar trafico. Sin esto, trafico iria a instancias muertas.

- **Como funciona SSL/TLS termination en el ALB?**
  El ALB hace el handshake TLS con el cliente. Entre ALB y tus tasks el trafico va HTTP (dentro de la VPC privada). Esto descarga a tus apps del costo CPU de TLS y centraliza el manejo de certificados.

- **Que es path-based routing vs host-based routing?**
  Path-based: rules por URL path (`/api/*` → service A). Host-based: rules por hostname (`api.app.com` → service A, `www.app.com` → service B). Ambos soportados en ALB.

- **Como haces zero-downtime deploy con ALB + ECS?**
  ECS hace rolling update: lanza tasks nuevas, las registra en el TG, espera que pasen healthchecks, despues desregistra las viejas. El ALB nunca manda trafico a tasks no-healthy.

### Duracion estimada
3-4 horas.

---

## Fase 7: Observabilidad con CloudWatch

### Objetivo
Ver que esta pasando en produccion. Logs centralizados, metricas, alarmas.

### Conceptos nuevos

#### Pilares de observabilidad

1. **Logs:** registros de eventos (qué pasó)
2. **Metrics:** numeros a lo largo del tiempo (CPU, requests/sec, latencia)
3. **Traces:** el viaje de un request a traves de multiples servicios (distributed tracing)

En AWS los 3 estan cubiertos por CloudWatch (metrics + logs) + X-Ray (traces).

#### CloudWatch Logs
Almacen centralizado de logs. Tus apps/containers escriben logs, CloudWatch los junta, podes hacer queries.

**Estructura:**
- **Log Group:** agrupacion logica (ej: `/ecs/property-api`)
- **Log Stream:** un flujo especifico (ej: una task particular)
- **Log Event:** un log individual

**Retention:** los logs se guardan para siempre por default. Costo. Define retention (ej: 30 dias) para que borre automaticamente.

#### CloudWatch Logs Insights
Query language para buscar en logs:
```
fields @timestamp, @message
| filter @message like /ERROR/
| sort @timestamp desc
| limit 100
```

#### CloudWatch Metrics
Numeros a lo largo del tiempo. AWS publica metricas automaticamente para cada servicio:
- EC2: CPU, network, disk
- ALB: requests, latency, 4xx/5xx errors
- ECS: CPU/memory utilization
- RDS: connections, storage, CPU

Tambien podes publicar custom metrics desde tu app (ej: "orders processed per minute").

#### Alarma
Regla: "si metrica X supera Y por Z minutos, alertar". Ejemplos:
- CPU de property-api > 80% por 5 min → email
- ALB 5xx errors > 10/min → PagerDuty
- RDS storage < 10% free → alerta critica

#### SNS (Simple Notification Service)
Lo usas para notificar alarmas. Creas un topic, te subscribis (email, SMS, Lambda, PagerDuty). La alarma publica al topic, los subscriptores reciben.

#### X-Ray (distributed tracing)
Ve el viaje completo de un request: gateway → property-api → pricing-api → RDS. Cada "span" muestra duracion y errores. Critico para debuggear microservicios.

### Pasos

1. **Configurar retention en log groups**
   ```bash
   aws logs put-retention-policy \
     --log-group-name /ecs/property-api \
     --retention-in-days 30
   ```

2. **Crear log metric filters** (extraer numeros de los logs)
   Ejemplo: contar errores 500
   ```bash
   aws logs put-metric-filter \
     --log-group-name /ecs/property-api \
     --filter-name "5xx-errors" \
     --filter-pattern '[ip, id, user, timestamp, request, status_code=5*, size]' \
     --metric-transformations \
         metricName=Api5xxErrors,metricNamespace=JavaTraining,metricValue=1
   ```

3. **Crear dashboard**
   - Consola > CloudWatch > Dashboards > Create
   - Widgets: CPU de ECS services, latencia ALB, 4xx/5xx, RDS connections

4. **Crear SNS topic para alarmas**
   ```bash
   aws sns create-topic --name alerts
   aws sns subscribe --topic-arn arn:xxx --protocol email --notification-endpoint tu@email.com
   ```

5. **Crear alarmas**
   ```bash
   aws cloudwatch put-metric-alarm \
     --alarm-name property-api-high-cpu \
     --metric-name CPUUtilization \
     --namespace AWS/ECS \
     --dimensions Name=ServiceName,Value=property-api-service Name=ClusterName,Value=java-training-cluster \
     --statistic Average \
     --period 300 \
     --evaluation-periods 2 \
     --threshold 80 \
     --comparison-operator GreaterThanThreshold \
     --alarm-actions arn:aws:sns:us-east-1:xxx:alerts
   ```

6. **(Opcional) Agregar Micrometer + Spring Actuator metrics**
   En tu app:
   ```xml
   <dependency>
     <groupId>io.micrometer</groupId>
     <artifactId>micrometer-registry-cloudwatch2</artifactId>
   </dependency>
   ```

7. **(Opcional) Agregar AWS X-Ray SDK** para distributed tracing

### Criterio de exito
- Los logs de las 3 apps aparecen en CloudWatch
- Tenes un dashboard con las metricas clave
- Al menos 3 alarmas configuradas con SNS
- Podes simular un fallo (ej: tirar abajo una task) y recibir el email

### Preguntas de entrevista
- **Los 3 pilares de observabilidad?**
  Logs (eventos), metrics (numeros), traces (viaje de un request). En AWS: CloudWatch Logs, CloudWatch Metrics, X-Ray.

- **Logs estructurados vs no-estructurados?**
  Estructurados (JSON): parseables, queryables, filtrables. No-estructurados: texto libre, dificil de analizar. Siempre estructurados en produccion.

- **Diferencia entre monitoring y observabilidad?**
  Monitoring: responde "esta funcionando?" (preguntas conocidas). Observabilidad: permite responder "por que falla?" (preguntas no anticipadas). Monitoring es un subset de observability.

- **Como debuggeas un request lento que atraviesa 5 microservicios?**
  Distributed tracing (X-Ray). Cada servicio anota los spans. Ves en un graph el tiempo en cada servicio, identificas el bottleneck.

- **Que son los 4 Golden Signals?**
  Traffic (cuanto), Latency (que tan rapido), Errors (cuantos fallan), Saturation (que tan lleno esta el sistema). Framework de Google SRE.

### Duracion estimada
4-5 horas.

---

## Fase 8: Messaging asincronico

### Objetivo
Aprender comunicacion asincronica entre servicios. Publicar eventos de tu app a SNS/SQS, procesarlos con Lambda.

### Conceptos nuevos

#### Por que asincronico?
Flujo sincronico (lo que tenes ahora):
```
Cliente → POST /properties → property-api → RDS
                                 ↓
                              espera OK
                                 ↓
                              responde al cliente
```

Si al crear property queres: enviar email, indexar en search, notificar a otro servicio — cada uno suma latencia. Si uno falla, todo falla.

Flujo asincronico:
```
Cliente → POST /properties → property-api → RDS
                                 ↓
                              publica evento
                                 ↓
                              responde al cliente (rapido!)
                                 ↓
                    [otros servicios procesan el evento en paralelo]
```

#### SQS (Simple Queue Service)
Cola de mensajes durable. Un productor pone mensajes, uno o mas consumidores los sacan.

**Garantias:**
- At-least-once delivery (puede entregar el mismo mensaje 2 veces — por eso necesitas idempotency)
- Order: **Standard** queue no garantiza orden. **FIFO** queue si (mas cara).
- Retention: hasta 14 dias.

**Dead Letter Queue (DLQ):** si un mensaje falla N veces, va a una cola aparte (DLQ) para investigacion manual.

#### SNS (Simple Notification Service)
Pub-sub. Un publisher manda a un topic, N subscribers reciben.

**Diferencia con SQS:**
- SQS: cola 1-a-1 (un mensaje, un consumidor lo procesa)
- SNS: fanout 1-a-N (un mensaje, todos los subscribers lo reciben)

#### Pattern: SNS + SQS (fanout)
Publisher → SNS topic → subscribe varias SQS queues → cada una procesada por un consumer.

Ventaja: desacoplado. Cada consumer tiene su propia queue (retry independiente, DLQ propio).

#### Lambda
Compute serverless. Le das codigo, AWS lo corre cuando es invocado. Perfecto para consumer de SQS.

- Max duration: 15 minutos
- Max memory: 10 GB
- Timeout y memoria configurables
- Integrado con SQS: Lambda polla la cola automaticamente, procesa batches

#### EventBridge
Evolucion de SNS + reglas. Permite:
- Schemas y discovery
- Reglas mas expresivas (filtros por contenido)
- Multiples destinos (SQS, Lambda, Kinesis, etc.)
- Eventos de otros servicios AWS automaticamente

Cuando usar que:
- **SNS:** pub-sub simple y barato
- **EventBridge:** eventos con filtrado inteligente, integracion con servicios AWS

### Caso de uso en tu proyecto

Tu property-api ya tiene `PropertyCreatedEvent` (Spring event interno). Vamos a:
1. Publicar ese evento a SNS cuando se crea una property
2. Tener una SQS queue subscrita al topic
3. Una Lambda consumiendo la queue que, por ejemplo, envia un "email de confirmacion"

### Pasos

1. **Crear SNS topic**
   ```bash
   aws sns create-topic --name property-events
   ```

2. **Crear SQS queue**
   ```bash
   aws sqs create-queue --queue-name property-events-notifications
   ```

3. **Crear DLQ para la queue**
   ```bash
   aws sqs create-queue --queue-name property-events-notifications-dlq
   ```

4. **Subscribir la queue al topic**
   ```bash
   aws sns subscribe \
     --topic-arn arn:aws:sns:...:property-events \
     --protocol sqs \
     --notification-endpoint arn:aws:sqs:...:property-events-notifications
   ```

5. **Agregar permiso para que SNS pueda escribir a la SQS**

6. **Actualizar property-api para publicar a SNS**
   ```java
   @Component
   public class SnsPublisher {
       private final SnsClient snsClient;
       private final String topicArn;

       @EventListener
       public void handle(PropertyCreatedEvent event) {
           snsClient.publish(PublishRequest.builder()
               .topicArn(topicArn)
               .message(objectMapper.writeValueAsString(event))
               .build());
       }
   }
   ```

7. **Agregar permisos al task role** (publish a SNS)

8. **Crear Lambda consumer**
   - Codigo simple en Java/Python/Node
   - Event source: la SQS queue
   - Al recibir mensaje: log "processing event X" (simulacion de enviar email)

9. **Probar end-to-end**
   - POST a /api/properties
   - Ver en CloudWatch Logs: Lambda ejecutada, log del event

### Criterio de exito
- SNS topic + SQS queue creados
- property-api publica eventos a SNS
- Lambda consume de SQS y los procesa
- DLQ configurada
- Al crear una property, ves el evento en los logs de Lambda

### Preguntas de entrevista
- **SQS vs SNS vs EventBridge?**
  SQS: cola 1-a-1. SNS: pub-sub fanout. EventBridge: pub-sub con filtrado avanzado e integracion con servicios AWS. SQS+SNS es el pattern clasico, EventBridge es mas nuevo y potente.

- **At-least-once vs exactly-once?**
  At-least-once: el mensaje puede entregarse multiples veces (duplicados posibles). Exactly-once: garantiza una sola vez. Es dificil/imposible en sistemas distribuidos; se emula con at-least-once + idempotency en el consumer.

- **Que es una DLQ y cuando la usas?**
  Dead Letter Queue. Mensajes que fallaron N veces van ahi para investigacion manual. Sin DLQ, un mensaje malo puede bloquear toda la queue en un loop infinito de retries.

- **Como garantizas idempotency en un consumer?**
  Cada mensaje tiene un ID unico. Antes de procesarlo, checkear en DynamoDB/Redis si ya fue procesado. Si si, skip. Si no, procesar + marcar como procesado.

- **Diferencia entre SQS Standard y FIFO?**
  Standard: at-least-once, sin orden, throughput ilimitado. FIFO: exactly-once, orden estricto, 300 msg/sec por message group (mas caro).

### Duracion estimada
4-5 horas.

---

## Fase 8b: Streaming con Kinesis

### Objetivo
Aprender streaming de eventos (diferente a queues). Procesar eventos en tiempo real con Lambda.

### Conceptos nuevos

#### Queue vs Stream

| | Queue (SQS) | Stream (Kinesis, Kafka) |
|-|-------------|-------------------------|
| Modelo | Pull, delete | Replay, offset |
| Consumidores | Compiten por mensajes | Cada uno lee independiente |
| Orden | Standard: no. FIFO: si | Dentro de shard: si |
| Retention | Hasta 14 dias | 1 dia default, hasta 365 |
| Replay | No (mensaje leido → eliminado) | Si (replay desde cualquier punto) |
| Uso tipico | Task queue | Analytics, event sourcing, CDC |

#### Kinesis Data Streams
Stream de eventos ordenados, dividido en **shards**. Cada shard es una unidad de throughput: 1 MB/s escritura, 2 MB/s lectura.

**Consumidores:**
- **Classic consumers:** pollean el stream (hasta 5 apps por shard, 2 MB/s compartidos)
- **Enhanced fan-out:** cada consumer tiene 2 MB/s dedicado

#### Partition key
Determina a que shard va cada evento. Misma partition key = mismo shard = orden garantizado.

Ejemplo: partition key = `userId`. Todos los eventos del usuario X van al mismo shard, en orden.

#### Offset / Sequence Number
Cada evento tiene un sequence number unico en su shard. El consumer mantiene su "posicion" (checkpoint). Si se reinicia, continua desde ahi.

### Caso de uso

Stream de eventos de "property views" (cada vez que alguien ve una property):
1. property-api publica un evento por cada GET /properties/{id}
2. Kinesis ingesta a alta velocidad
3. Lambda consume y:
   - Agrega contadores en DynamoDB ("propiedad X vista 127 veces")
   - Detecta trending properties (moving window de 1 hora)

### Pasos

1. **Crear stream**
   ```bash
   aws kinesis create-stream \
     --stream-name property-views \
     --shard-count 1
   ```

2. **Actualizar property-api** para publicar eventos
   ```java
   kinesisClient.putRecord(PutRecordRequest.builder()
       .streamName("property-views")
       .partitionKey(propertyId.toString())
       .data(SdkBytes.fromString(json, StandardCharsets.UTF_8))
       .build());
   ```

3. **Crear Lambda consumer**
   - Event source: Kinesis stream
   - Batch size: ej 100 records
   - Starting position: LATEST

4. **Lambda code (pseudocodigo)**
   ```java
   public void handleRequest(KinesisEvent event) {
       for (KinesisEvent.KinesisEventRecord record : event.getRecords()) {
           String data = new String(record.getKinesis().getData().array());
           PropertyViewEvent e = parse(data);
           dynamoDbClient.updateItem(...);  // incrementa contador
       }
   }
   ```

### Criterio de exito
- Stream creado
- property-api publica al stream cuando alguien ve una property
- Lambda consume y actualiza contadores en DynamoDB
- Podes simular trafico y ver metrics de throughput en CloudWatch

### Preguntas de entrevista
- **Cuando usarias Kinesis vs SQS?**
  Kinesis: streaming de alto volumen, replay, orden por partition key, multiples consumers independientes. SQS: task queue, procesar y descartar, competencia de consumers.

- **Kinesis vs Kafka?**
  Kinesis: fully managed AWS, pagas por shard + requests. Kafka: open source, mas features (compactacion, exactly-once producer, stream processing con ksqlDB). Para AWS-only: Kinesis. Para multi-cloud o features avanzados: MSK (managed Kafka) o Kafka self-hosted.

- **Que es un shard y como escalas?**
  Unidad de throughput: 1 MB/s write, 2 MB/s read. Si necesitas mas, agregas shards. Kinesis puede reshardearse en vivo (split/merge shards).

- **Como manejas hot partition?**
  Mismo problema que DynamoDB. Si mucho trafico va a una partition key, un shard se satura. Solucion: partition key mas distribuida, o usar multiples keys (ej: `userId_bucket`).

- **Cual es la retention tipica y por que?**
  Default 24 horas, puede subirse a 365 dias (paga). Util para replay si un consumer tiene bug y necesitas reprocesar.

### Duracion estimada
4 horas.

---

## Fase 9: CloudFront + WAF

### Objetivo
Acelerar tu API globalmente (CDN) y protegerla de ataques comunes.

### Conceptos nuevos

#### CDN (Content Delivery Network)
Red global de servidores cercanos a los usuarios. El primer request va al origen (tu ALB), despues se cachea cerca del usuario. Los siguientes requests del mismo contenido son mucho mas rapidos.

**Para APIs:** ademas de cache (si aplica), provee terminacion SSL cerca del usuario, DDoS protection basica, punto de entrada unico global.

#### CloudFront
CDN de AWS. Tiene "Edge Locations" en todo el mundo (~400).

**Caracteristicas utiles para APIs:**
- TLS 1.3 en el edge
- Compresion (gzip, brotli)
- HTTP/2 y HTTP/3
- Cache configurable por path
- Integracion con WAF
- Proteccion DDoS con AWS Shield Standard (gratis)

#### WAF (Web Application Firewall)
Filtra requests maliciosos antes de que lleguen a tu app. Reglas:
- SQL injection
- XSS
- Rate limiting (ej: max 100 req/min por IP)
- Geo blocking (bloquear paises)
- IP reputation lists

**AWS Managed Rules:** reglas pre-armadas por AWS, se actualizan automaticamente.

### Pasos

1. **Crear distribucion de CloudFront**
   - Origin: tu ALB
   - Default cache behavior: no cachear (APIs dinamicas)
   - Si tenes endpoints GET que se pueden cachear (ej: `/api/properties` publico), cache selectivo por path

2. **Crear WebACL en WAF**
   - Add rules:
     - AWS Managed Rules: Core rule set + Known bad inputs
     - Rate limiting: 2000 requests / 5 min por IP
     - (Opcional) Geo blocking

3. **Asociar WebACL a CloudFront distribution**

4. **Actualizar DNS** (si tenes dominio)
   - Point `api.tu-dominio.com` a la distribution de CloudFront (CNAME o ALIAS en Route53)

### Criterio de exito
- Distribution de CloudFront activa
- WAF protegiendo con managed rules + rate limiting
- Requests pasan por CloudFront → WAF → ALB → ECS
- Podes testear rate limiting excediendo el threshold

### Preguntas de entrevista
- **Cuando usar CloudFront para una API?**
  Siempre que quieras terminacion SSL global, proteccion DDoS basica, logging centralizado. Para APIs puramente dinamicas el cache no aplica pero los otros beneficios si.

- **Que es un WAF y que problemas resuelve?**
  Firewall de capa 7. Bloquea patrones maliciosos (SQLi, XSS), rate limiting, bot protection. Sin esto tu app recibe directamente requests agresivos.

- **Diferencia entre WAF y Security Group?**
  Security Group: capa 3-4, allow/deny por IP y puerto. WAF: capa 7, inspecciona el contenido HTTP (query params, headers, body). Se complementan.

- **Que es rate limiting y donde lo implementas?**
  Limitar requests por tiempo (ej: 100/min por IP). Se implementa en WAF (externo) o API Gateway (si usas AWS API Gateway) o en tu app (Resilience4j Bulkhead).

- **AWS Shield Standard vs Advanced?**
  Standard: gratis, proteccion basica contra DDoS de capa 3-4. Advanced: ~$3000/mes, proteccion avanzada incluso capa 7, soporte 24/7 del equipo DDoS Response Team.

### Duracion estimada
2-3 horas.

---

## Fase 10: Terraform

### Objetivo
Convertir toda la infra creada manualmente (o con AWS CLI) a codigo Terraform. Reproducible, versionable, revisable.

### Conceptos nuevos

#### Infrastructure as Code
Filosofia: tu infraestructura es codigo. Ventajas:
- Reproducible: `terraform apply` crea todo igual en dev/staging/prod
- Versionable: diff entre cambios
- Revisable: PR con cambios de infra
- Documentado: el codigo ES la documentacion

#### Terraform
Herramienta open source de HashiCorp. Multi-cloud. Lenguaje: HCL.

**Conceptos:**
- **Resource:** algo que Terraform crea (AWS VPC, RDS instance, etc.)
- **Provider:** el cloud (AWS, GCP, Azure)
- **Module:** codigo reutilizable
- **State:** archivo que Terraform mantiene con el estado actual de la infra
- **Plan:** preview de cambios antes de aplicar
- **Apply:** ejecuta los cambios

#### State
Archivo JSON que Terraform mantiene para saber "que ya cree y que no". Critical:
- **Local state:** archivo en tu maquina (NO hacer esto en equipo)
- **Remote state:** en S3 con locking via DynamoDB. **Estandar profesional.**

#### Backend
Donde vive el state. Para produccion: S3 bucket + DynamoDB para locking.

```hcl
terraform {
  backend "s3" {
    bucket         = "mi-tf-state"
    key            = "java-training/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "tf-locks"
    encrypt        = true
  }
}
```

### Estructura propuesta

```
infra/
├── modules/
│   ├── networking/      # VPC, subnets, IGW, NAT
│   ├── ecs-service/     # reusable ECS service
│   ├── rds/
│   └── observability/
├── envs/
│   ├── dev/
│   │   ├── main.tf      # compone los modulos
│   │   ├── variables.tf
│   │   └── terraform.tfvars
│   ├── staging/
│   └── prod/
└── bootstrap/           # crea el bucket S3 y la tabla DynamoDB para el state
```

### Pasos

1. **Bootstrap: crear S3 + DynamoDB para state** (una vez)

2. **Crear modulo `networking`**
   ```hcl
   # modules/networking/main.tf
   resource "aws_vpc" "main" {
     cidr_block = var.vpc_cidr
     tags = { Name = "${var.name}-vpc" }
   }

   resource "aws_subnet" "public" {
     count             = length(var.azs)
     vpc_id            = aws_vpc.main.id
     cidr_block        = var.public_subnet_cidrs[count.index]
     availability_zone = var.azs[count.index]

     map_public_ip_on_launch = true
     tags = { Name = "${var.name}-public-${var.azs[count.index]}" }
   }
   # ... subnet privadas, IGW, NAT, route tables
   ```

3. **Crear modulo `ecs-service`** reutilizable

4. **Crear environment `dev`** que use los modulos

5. **Aplicar**
   ```bash
   cd infra/envs/dev
   terraform init
   terraform plan    # preview
   terraform apply   # ejecuta
   ```

6. **Destruir lo creado manualmente** y dejar que Terraform lo maneje

### Criterio de exito
- Toda la infraestructura en codigo
- `terraform destroy` borra todo, `terraform apply` lo recrea
- State en S3 + DynamoDB (no local)
- Modulos reutilizables
- CI/CD (fase 11) corre `terraform plan` en cada PR

### Preguntas de entrevista
- **Terraform vs CloudFormation?**
  Terraform: open source, multi-cloud, HCL language. CloudFormation: AWS-only, YAML/JSON, integrado con AWS. Terraform es mas popular en general; CloudFormation si la empresa es AWS-heavy.

- **Que es state y por que importa?**
  Archivo que Terraform usa para saber que recursos existen. Sin state, Terraform no sabe si un recurso ya fue creado. Remote state con locking es critico en equipo (evita que 2 personas apliquen al mismo tiempo).

- **Que es un module en Terraform?**
  Coleccion reusable de resources. Ejemplo: modulo "networking" que crea VPC + subnets + routing. Lo llamas desde diferentes environments.

- **Terraform vs Ansible vs CDK?**
  Terraform: infra declarativa (que queres, no como). Ansible: configuration management de maquinas (SSH + playbooks). CDK: infra como codigo en lenguajes (Java, TypeScript, Python) que genera CloudFormation.

- **Como manejas secrets en Terraform?**
  Nunca en el codigo. Lees de Secrets Manager o Parameter Store con data sources. El state puede contener valores sensibles, por eso se cifra en S3.

### Duracion estimada
6-8 horas.

---

## Fase 11: GitHub Actions CI/CD

### Objetivo
Automatizar el deploy. Push a main → tests → build imagen → push a ECR → actualizar ECS service.

### Conceptos nuevos

#### CI (Continuous Integration)
Cada push corre tests automaticamente. Previene que codigo roto llegue a main.

#### CD (Continuous Deployment)
Cada push a main se despliega automaticamente (si los tests pasan). No hay "deploy manual del viernes".

#### Pipeline
Secuencia de steps: checkout → build → test → docker build → push → deploy.

#### GitHub Actions
Sistema de CI/CD de GitHub. Los pipelines son archivos YAML en `.github/workflows/`.

#### Secrets en GitHub
Para credentials de AWS (o idealmente: OIDC roles). Nunca commitear.

#### OIDC (OpenID Connect) con AWS
En vez de guardar access keys de AWS en GitHub, configuras un IAM role que GitHub puede asumir via OIDC. **Estandar profesional.**

### Pipeline propuesto

```yaml
# .github/workflows/deploy.yml
name: Deploy

on:
  push:
    branches: [main]

permissions:
  id-token: write   # para OIDC
  contents: read

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - run: ./mvnw clean test

  build-and-push:
    needs: test
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [property-api, pricing-api, api-gateway]
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::xxx:role/github-actions-role
          aws-region: us-east-1
      - uses: aws-actions/amazon-ecr-login@v2
      - run: |
          docker build -t ${{ matrix.service }}:${{ github.sha }} \
            -f ${{ matrix.service }}/Dockerfile .
          docker tag ${{ matrix.service }}:${{ github.sha }} \
            xxx.dkr.ecr.us-east-1.amazonaws.com/${{ matrix.service }}:${{ github.sha }}
          docker push xxx.dkr.ecr.us-east-1.amazonaws.com/${{ matrix.service }}:${{ github.sha }}

  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::xxx:role/github-actions-role
          aws-region: us-east-1
      - run: |
          # Actualiza el ECS service forzando new deployment
          aws ecs update-service \
            --cluster java-training-cluster \
            --service property-api-service \
            --force-new-deployment
```

### Pasos

1. **Configurar OIDC entre GitHub y AWS**
   - Crear IAM OIDC provider para `token.actions.githubusercontent.com`
   - Crear role con trust policy que solo tu repo pueda asumir

2. **Crear pipeline** (archivo yaml arriba)

3. **Agregar workflow de PR**
   - En PRs: test + terraform plan (sin apply)
   - En main: test + deploy

4. **Agregar tests de humo post-deploy**
   ```yaml
   - name: Smoke test
     run: |
       curl -f https://api.tu-dominio.com/actuator/health || exit 1
   ```

### Criterio de exito
- Push a main dispara el pipeline
- Pipeline corre tests, builda, pushea, despliega
- Si el test falla, no deploya
- Zero-downtime deploy (ECS rolling update)
- Usa OIDC, no access keys

### Preguntas de entrevista
- **CI vs CD?**
  CI: cada push se compila y testea. CD: cada push que pasa tests se despliega. Continuous Deployment (automatico) vs Continuous Delivery (listo para deploy manual).

- **Como manejas secrets en CI?**
  GitHub Secrets para credenciales generales. OIDC para cloud (no guardas access keys). Para secrets de aplicacion: Secrets Manager/Parameter Store, la app los lee en runtime.

- **Blue-green deployment vs rolling update vs canary?**
  Rolling: reemplaza tasks de a poco. Blue-green: levantas ambiente nuevo (green), testeas, switcheas trafico. Canary: routeas 1% de trafico al nuevo, observas, subis gradualmente.

- **Que es infrastructure drift y como evitarlo?**
  Cuando la infra real diverge del codigo (alguien hizo un cambio manual). Se evita: (1) disciplina, no cambios manuales, (2) `terraform plan` periodico en CI, alerta si hay drift.

- **Como haces rollback si un deploy falla?**
  ECS: actualizas el service con la task definition anterior (version previa). Terraform: `terraform apply` con el codigo anterior. Siempre versionas las task definitions e imagenes, nunca uses `latest`.

### Duracion estimada
4-5 horas.

---

## Fase 12: Testing BDD con Cucumber

### Objetivo
Agregar tests de aceptacion escritos en lenguaje natural. Verifican flujos end-to-end desde el punto de vista de negocio.

### Conceptos nuevos

#### BDD (Behavior-Driven Development)
Filosofia donde los tests son escritos en lenguaje natural (`Given-When-Then`). Product, QA, y devs leen los mismos specs.

#### Gherkin
Lenguaje de Cucumber:
```gherkin
Feature: Property creation
  Scenario: Admin creates a property successfully
    Given a user with role ADMIN
    When they send POST /api/properties with valid data
    Then the response status is 201
    And the property appears in GET /api/properties
```

#### Cucumber
Framework que ejecuta Gherkin. Cada `step` (Given/When/Then) se mapea a un metodo Java.

#### Pyramid of testing
```
      /\
     /  \  E2E (pocos, lentos, caros)  ← aqui entra Cucumber
    /----\
   /      \ Integration tests
  /--------\
 /          \ Unit tests (muchos, rapidos)
 ------------
```

### Pasos

1. **Agregar dependencias**
   ```xml
   <dependency>
     <groupId>io.cucumber</groupId>
     <artifactId>cucumber-java</artifactId>
     <version>7.x</version>
     <scope>test</scope>
   </dependency>
   <dependency>
     <groupId>io.cucumber</groupId>
     <artifactId>cucumber-spring</artifactId>
     <scope>test</scope>
   </dependency>
   ```

2. **Crear features**
   ```
   src/test/resources/features/
     property-creation.feature
     authentication.feature
   ```

3. **Implementar step definitions**
   ```java
   public class PropertySteps {
       @Autowired private MockMvc mockMvc;

       @Given("a user with role {word}")
       public void aUserWithRole(String role) { ... }

       @When("they send POST {string} with valid data")
       public void sendPost(String url) { ... }

       @Then("the response status is {int}")
       public void responseStatus(int status) { ... }
   }
   ```

4. **Configurar runner**
   ```java
   @Suite
   @IncludeEngines("cucumber")
   @SelectClasspathResource("features")
   @ConfigurationParameter(
       key = Constants.GLUE_PROPERTY_NAME,
       value = "com.mauricio.propertyapi.cucumber"
   )
   public class CucumberTest { }
   ```

5. **Incorporar al pipeline CI** (Fase 11)

### Criterio de exito
- Features escritos en Gherkin
- Step definitions funcionando
- `./mvnw verify` corre unit + integration + Cucumber
- CI corre los features en cada PR

### Preguntas de entrevista
- **BDD vs TDD?**
  TDD: escribo test unitario primero, despues codigo. Escala de codigo. BDD: escribo spec de negocio (feature), despues implementacion. Escala de negocio/producto.

- **Testing pyramid?**
  Muchos unit tests (rapidos), menos integration, pocos E2E. Invertir la piramide (mayoria E2E) hace tests lentos y fragiles.

- **Cucumber vs Postman/Newman para tests de API?**
  Cucumber: tests en lenguaje natural, integracion con codigo Java. Postman: test en UI, mas facil para QA no-dev. Complementarios.

- **Flaky tests?**
  Tests que a veces pasan y a veces no. Causas: dependencias externas, timing, state compartido. Solucion: aislar, mockear, usar waits deterministas.

### Duracion estimada
3-4 horas.

---

# Parte 4: Apendice

## Costos estimados

Con alla de Free Tier y las 12 fases corriendo:

| Recurso | Costo/mes aprox |
|---------|-----------------|
| NAT Gateway | ~$32 (el mas caro) |
| RDS t3.micro Multi-AZ | ~$30 |
| ECS Fargate (3 services, 2 tasks cada, 0.5 vCPU) | ~$40 |
| ALB | ~$18 |
| CloudFront + WAF | ~$5 |
| S3 + DynamoDB + SQS + Lambda + Kinesis (bajo uso) | ~$5 |
| CloudWatch Logs | ~$5 |
| **Total** | **~$135/mes** |

**Como reducir mientras practicas:**
- **Destruir infra cuando no estas trabajando.** Con Terraform: `terraform destroy` al finalizar la sesion. Vuelves a levantarla con `terraform apply` cuando retomes.
- **Usar RDS t3.micro single-AZ** (sin Multi-AZ) durante practica: ~$15/mes
- **1 sola task por service** en vez de 2
- **Eliminar NAT Gateway** mientras practicas: usar VPC endpoints para ECR y CloudWatch (los mas criticos)

Practicando 2-3 horas/dia y destruyendo al terminar: **~$20-30/mes real.**

## Orden recomendado de estudio

Si alguien te llama para entrevista en 2 semanas y no podes hacer todas las fases:

**Prioridad 1 (imprescindible):**
- Fase 0, 1, 2 (fundamentos)
- Fase 3, 4 (containers + compute)
- Fase 5 (RDS)
- Fase 6 (ALB)
- Fase 7 (observabilidad)

**Prioridad 2 (si tenes tiempo):**
- Fase 5b (DynamoDB)
- Fase 8 (SQS/SNS/Lambda)
- Fase 10 (Terraform)
- Fase 11 (CI/CD)

**Prioridad 3 (especializacion):**
- Fase 8b (Kinesis)
- Fase 9 (CloudFront/WAF)
- Fase 12 (Cucumber)

## Recursos adicionales

### AWS
- [AWS Well-Architected Framework](https://aws.amazon.com/architecture/well-architected/) — principios de diseno en AWS
- [AWS Workshops](https://workshops.aws/) — tutoriales hands-on
- [AWS Skill Builder](https://skillbuilder.aws/) — cursos oficiales

### Java + Spring en AWS
- [Spring Cloud AWS](https://awspring.io/) — integracion Spring con AWS
- [AWS SDK for Java v2 docs](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/)

### Terraform
- [Terraform AWS Provider docs](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [Gruntwork: Terraform Up & Running](https://www.oreilly.com/library/view/terraform-up/9781098116736/) — libro clasico

### Conceptos generales
- [System Design Primer](https://github.com/donnemartin/system-design-primer)
- [Google SRE books](https://sre.google/books/)

---

## Proximo paso

Lee la Fase 0 completa y cuando tengas:
- Cuenta AWS
- Billing alerts configurados
- AWS CLI funcionando
- Usuario IAM (no root) creado

Me avisas y empezamos la **Fase 1** con detalle hands-on (comandos exactos, capturas conceptuales, troubleshooting comun).
