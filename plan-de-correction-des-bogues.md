# Plan de correction des bogues - AnzenFS

Ce document qualifie chaque anomalie que j'ai corrigée au fil du développement : sévérité, reproductibilité, cause racine et correction apportée. Plutôt que de m'arrêter aux titres de mes messages de correction dans Git - souvent trop courts ou pas exhaustifs -, j'ai réexaminé le diff réel de chaque correctif pour en extraire la cause racine précise : plusieurs corrections regroupent en réalité plusieurs bugs distincts que leur seul intitulé ne laissait pas deviner.

Il complète [`compte_rendu.md`](compte_rendu.md) (section C2.3.2) et [`cahier-de-recettes.md`](cahier-de-recettes.md).

## Synthèse

| ID | Résumé | Sévérité |
|---|---|---|
| BUG-01 | Téléchargement en échec pour les fichiers situés dans un sous-dossier | Majeure |
| BUG-02 | Prévisualisation cassée : 403 systématique + lecture vidéo non progressive | Majeure |
| BUG-03 | Upload en échec : paramètre JDBC mal indexé + hash calculé sur un chemin non résolu | Critique |
| BUG-04 | Traversée de répertoire lors de l'upload (écriture hors du stockage) | Critique (sécurité) |
| BUG-05 | Exception sur la recherche d'un élément à la racine (sans parent) | Mineure |

---

## BUG-01 - Téléchargement en échec pour les fichiers en sous-dossier

- **Sévérité :** Majeure
- **Reproductibilité :** Systématique, pour tout fichier qui n'est pas directement à la racine du dossier de l'utilisateur

**Cause racine.** Le chemin physique de destination d'un fichier uploadé était calculé en résolvant directement le chemin relatif transmis par le client (`storageRoot.resolve(incomingPath)`), sans tenir compte de la hiérarchie réelle des dossiers enregistrée en base. Le hash du fichier était lui aussi calculé sur ce même chemin brut. Résultat : un fichier pouvait être physiquement écrit à un emplacement disque différent de celui que la route de téléchargement reconstruisait ensuite à partir de la hiérarchie de dossiers en base - le fichier attendu n'y était donc pas.

**Correction.** Ajout d'une méthode `FileRepository.getFullPathById` (requête SQL récursive `WITH RECURSIVE` qui remonte la chaîne de parenté d'un dossier) pour calculer le véritable chemin de destination à partir de l'identifiant de dossier résolu en base, et non plus à partir du chemin brut envoyé par le client. Le hash est désormais calculé sur ce chemin physique réel.

**Test de non-régression :** `FileServiceTest.processFolderUploadCreatesMissingSubfolderHierarchy`, `FileServiceTest.processFolderUploadReusesExistingSubfolder`, `FileControllerTest.downloadFileReturnsResourceWithContentDisposition`.

---

## BUG-02 - Prévisualisation cassée (403 systématique + vidéos non lisibles en continu)

- **Sévérité :** Majeure
- **Reproductibilité :** Systématique - 100 % des tentatives de prévisualisation échouaient avant correction

Ce correctif regroupe en réalité **deux bugs indépendants**, l'un frontend, l'autre backend, que son seul intitulé ne distinguait pas :

**Cause racine 1 (frontend).** Les prévisualisations étaient chargées via une URL de ressource native (`<img src="...">` pointant directement vers `/api/files/preview/{id}`). Or cette route est protégée par JWT, et un navigateur n'attache jamais d'en-tête `Authorization` personnalisé à un chargement de ressource natif (contrairement aux appels passant par `HttpClient`, interceptés par `auth-interceptor.ts`). Chaque tentative de prévisualisation recevait donc un `403 Forbidden`, quel que soit le format du fichier.

**Correction 1.** `StoreService.getPreview()` récupère désormais l'image en `blob` via `HttpClient` (l'intercepteur d'authentification s'applique normalement), puis le composant crée une URL d'objet (`URL.createObjectURL`) liée à cette blob pour l'afficher.

**Cause racine 2 (backend).** L'endpoint `/api/files/preview/{externalId}` ne gérait pas les requêtes par plages (`Range`), renvoyant systématiquement le fichier entier - ce qui empêche la lecture progressive/le seek d'une vidéo dans le navigateur. Il détectait par ailleurs le type de contenu via `Files.probeContentType()`, une détection dépendante du système d'exploitation et peu fiable (retourne souvent `null` sur Linux pour de nombreux formats), risquant de servir un fichier avec un type MIME générique et donc de casser son rendu inline.

**Correction 2.** L'endpoint gère désormais les en-têtes `Range` via `ResourceRegion`/`HttpRange` de Spring (réponse `206 Partial Content` le cas échéant), et détecte le type MIME via `MediaTypeFactory` (basé sur l'extension du fichier, indépendant de l'OS).

**Test de non-régression :** `frontend/src/app/services/store.spec.ts` (« issues a blob GET request through HttpClient so the auth interceptor applies ») pour la cause 1 ; `FileControllerTest.previewFileHonorsRangeHeaderWithPartialContent` pour la cause 2.

---

## BUG-03 - Upload en échec (paramètre JDBC mal indexé + hash sur chemin non résolu)

- **Sévérité :** Critique (empêchait l'upload de fonctionner correctement)
- **Reproductibilité :** Systématique

Là encore, deux bugs distincts regroupés sous un même correctif :

**Cause racine 1.** Dans `FileRepository.insertFile`, le hash et l'identifiant externe (`externalId`) étaient tous deux liés au **même index de paramètre JDBC** (`stmt.setString(4, hash)` puis `stmt.setString(4, externalId)`) : la seconde liaison écrasait la première avant l'exécution de la requête, si bien que l'identifiant externe n'était jamais correctement inséré en base.

**Cause racine 2.** Le hash du fichier était calculé en appelant `Files.size(file)`/`Files.getLastModifiedTime(file)` directement sur le chemin **relatif** reçu du client, sans le résoudre par rapport à la racine de stockage - ce qui levait une `NoSuchFileException` à l'exécution. Un bloc `catch (Exception e)` bien trop large masquait cette erreur réelle sous un message trompeur : « SHA-256 algorithm not found ».

**Correction.** Correction de l'index du paramètre JDBC (`setString(5, externalId)`), résolution du chemin complet avant tout accès disque (`storageRoot.resolve(file)`), et restriction du `catch` à `NoSuchAlgorithmException` uniquement, pour ne plus masquer une erreur différente sous un message erroné.

**Test de non-régression :** `FileRepositoryTest.insertFileCreatesRowWithGeneratedExternalId` couvre aujourd'hui la persistance correcte de l'identifiant externe. Le code JDBC brut à l'origine de ce bug (liaison manuelle des index de paramètres) a depuis été intégralement remplacé par `JdbcTemplate`, qui élimine structurellement cette classe d'erreur (plus de gestion manuelle des index de paramètres nommés/positionnels dans le reste du code).

---

## BUG-04 - Traversée de répertoire lors de l'upload

- **Sévérité :** Critique (sécurité)
- **Reproductibilité :** Systématique, exploitable par tout compte disposant d'un droit d'upload

**Cause racine.** Le chemin de destination d'un fichier uploadé était construit en résolvant directement le nom/chemin fourni par le client contre `storageRoot` (`storageRoot.resolve(relativePath)`), sans normalisation ni vérification de confinement. Un nom de fichier ou chemin relatif contenant des segments `../` (ou un chemin absolu) permettait donc d'écrire un fichier en dehors du répertoire de stockage prévu - un cas concret de path traversal / écriture arbitraire de fichier (cf. `compte_rendu.md`, mapping OWASP A01/A05).

**Correction.** Normalisation systématique du chemin cible (`Path.resolve(...).normalize()`) et vérification explicite `targetLocation.startsWith(storageRoot)` avant toute écriture disque, avec levée d'une `SecurityException` en cas de tentative d'échappement détectée.

**Test de non-régression :** `FileServiceTest.processFolderUploadRejectsPathEscapingStorageRoot`.

---

## BUG-05 - Exception sur la recherche d'un élément à la racine

- **Sévérité :** Mineure
- **Reproductibilité :** Systématique lors de la recherche d'un élément sans dossier parent (racine)

**Cause racine.** `findIdByNameAndParent` liait un `Integer parentId` potentiellement `null` directement à un paramètre JDBC de type primitif (`stmt.setInt(2, parentId)`). Lorsque `parentId` valait `null` (élément à la racine, sans parent), l'auto-unboxing Java levait une `NullPointerException`.

**Correction.** Construction conditionnelle de la requête SQL selon que `parentId` est `null` ou non (`parent_id IS NULL` vs `parent_id = ?`), en ne liant le paramètre que dans le second cas.

**Test de non-régression :** aucun directement rattachable à ce code précis - il s'agit d'un des tout premiers correctifs du projet (base `DBManager` en singleton avec JDBC brut), depuis intégralement remplacé par l'architecture actuelle (`JdbcTemplate` injecté par Spring, repositories dédiés). Les repositories actuels (`FileRepository`, `RoleRepository`, etc.) gèrent les valeurs nulles via des requêtes paramétrées sans manipulation manuelle d'index, ce qui élimine structurellement cette classe de bug plutôt que de la re-tester spécifiquement.

---

## Hors périmètre de ce document

Mon historique Git contient également une dizaine de correctifs supplémentaires que j'ai vérifiés un par un et qui portent tous sur la configuration de mon pipeline (nom de propriété, échappement de chaînes, `Content-Type` d'un appel API, identifiant de release, coquilles, version d'outil, options de sécurité Docker, chemin du cache Maven). Ce sont des ajustements de configuration CI/CD, pas des anomalies fonctionnelles détectées en recette : ils ne relèvent donc pas du périmètre de ce plan de correction, qui porte sur le comportement de l'application elle-même.
