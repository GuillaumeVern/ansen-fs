# AnzenFS

Gestionnaire de fichiers web sécurisé (backend Spring Boot + frontend Angular).

## Prérequis

- **GraalVM Community Edition 25** pour la compilation native (recommandé pour la production).
- Sinon, un **JDK 25** + **Maven** suffisent pour le mode JVM classique.
- **Node.js 22** est nécessaire pour le build du frontend Angular, mais il est installé automatiquement par Maven (`frontend-maven-plugin`) : aucune installation manuelle requise.

## Compilation

### Binaire natif (recommandé pour la production)

```
mvn clean package -Pnative native:compile
```

Produit un exécutable autonome `target/anzenfs`, sans dépendance à une JVM installée sur la machine cible.

### JAR classique (développement)

```
mvn clean package
mvn spring-boot:run
```

## Configuration

Aucun secret à fournir manuellement au démarrage :

- **Base de données** : SQLite, stockée automatiquement dans le répertoire de données applicatif.
- **Secret JWT** : généré aléatoirement (512 bits) au tout premier démarrage puis persisté dans ce même répertoire avec des permissions restreintes au propriétaire (`chmod 600`) — voir `JwtSecretStore`. Il n'est jamais versionné dans Git.

Le répertoire de données par défaut est `~/.local/share/anzenfs`. Il peut être redéfini via la variable d'environnement `XDG_DATA_HOME` (le répertoire utilisé sera alors `$XDG_DATA_HOME/anzenfs`) — utile pour isoler plusieurs environnements (staging/production) sur la même machine.

## Lancement

```
./target/anzenfs
```

ou, en mode JAR :

```
java -jar target/anzenfs-0.0.1-SNAPSHOT.jar
```

L'application écoute par défaut sur le port 8080 et sert à la fois l'API REST (`/api/**`) et le frontend Angular compilé.

## TLS / HTTPS

AnzenFS ne termine pas TLS lui-même : il est conçu pour être placé derrière un reverse proxy. Sur mon instance de production, j'utilise **Traefik**, qui délivre et renouvelle automatiquement un certificat **Let's Encrypt**.

Si vous installez AnzenFS vous-même, la mise en place d'une terminaison TLS (reverse proxy, certificat) est **de votre responsabilité** : n'exposez pas l'application directement sur Internet en HTTP sans un proxy TLS devant elle.

## Environnements et intégration continue

Le déploiement est piloté par un pipeline Jenkins (`Jenkinsfile`) :

1. Tests backend (JUnit + JaCoCo) et frontend (Vitest), avec audits de dépendances (`npm audit`, OWASP Dependency-Check).
2. Compilation native.
3. Publication d'une release GitHub taguée (`v1.0.<numéro de build>`).
4. Déploiement automatique sur l'environnement de **staging**.
5. Promotion manuelle vers l'environnement de **production** (exposé sur Internet), après validation.

## Mise à jour

```
git pull
mvn clean package -Pnative native:compile
```

Remplacez le binaire `target/anzenfs` sur le serveur puis redémarrez le service. Les migrations de schéma de base de données (backfills) s'exécutent automatiquement au démarrage si nécessaire.
