# Cahier de recettes - AnzenFS

Ce document liste, user story par user story, les scénarios de test fonctionnels et le résultat attendu pour chacun. Il complète ma suite automatisée (JUnit/Vitest pour les tests unitaires et d'intégration, Cypress pour les parcours de bout en bout - voir `compte_rendu.md`, sections C2.2.2 et C2.3.1) : ce tableau est le support que je rejoue manuellement avant chaque montée de version majeure, et qui sert de check-list de non-régression fonctionnelle lisible sans avoir à lire le code des tests.

**Légende de couverture**
- ✅ Couvert automatiquement (test unitaire/intégration et/ou spec Cypress) - rejoué à chaque build.
- 🔲 Non encore automatisé - à rejouer manuellement.

---

## 1. Authentification et gestion de session

| ID | Scénario | Étapes | Résultat attendu | Couverture |
|---|---|---|---|---|
| AUTH-01 | Connexion avec identifiants valides | Saisir un login/mot de passe corrects sur `/login` → soumettre | Redirection vers `/files`, jeton JWT stocké côté client, appels API suivants authentifiés | ✅ Cypress + `AuthControllerTest` |
| AUTH-02 | Connexion avec identifiants invalides | Saisir un mauvais mot de passe → soumettre | Message d'erreur affiché, `401 Unauthorized`, aucun jeton stocké | ✅ `AuthControllerTest` |
| AUTH-03 | Blocage après tentatives répétées | Échouer la connexion 5 fois de suite sur le même compte en moins d'une minute | La 6e tentative renvoie `429 Too Many Requests`, même avec les bons identifiants | ✅ `LoginRateLimiterTest`, `AuthControllerTest` |
| AUTH-04 | Accès à une route protégée sans être connecté | Appeler `/api/files/**` sans en-tête `Authorization` | `401`/`403` selon la route, aucune donnée exposée | ✅ `JwtAuthenticationFilterTest` |
| AUTH-05 | Déconnexion | Cliquer sur « Log out » | Jeton local supprimé, redirection vers `/login`, routes protégées de nouveau inaccessibles | 🔲 |

## 2. Navigation et gestion de l'arborescence de fichiers

| ID | Scénario | Étapes | Résultat attendu | Couverture |
|---|---|---|---|---|
| NAV-01 | Navigation dans un dossier | Cliquer sur une carte de type dossier | Le contenu du dossier s'affiche, le fil d'Ariane (breadcrumb) se met à jour | ✅ Cypress |
| NAV-02 | Retour en arrière via le fil d'Ariane | Cliquer sur un élément intermédiaire du breadcrumb | Retour au bon niveau de l'arborescence | 🔲 |
| NAV-03 | Création d'un dossier | Utiliser l'action de création de dossier, saisir un nom | Le dossier apparaît dans la liste, avec les droits `WRITE` accordés au rôle personnel du créateur | 🔲 |
| NAV-04 | Suppression d'un fichier | Cliquer sur l'action supprimer, confirmer | Le fichier disparaît de la liste, confirmation demandée avant suppression effective | ✅ Cypress (`onDeleteClick` couvert unitairement côté frontend) |
| NAV-05 | Suppression en cascade d'un dossier | Supprimer un dossier contenant des sous-éléments | Tous les descendants sont supprimés, les tailles affichées sur les dossiers parents sont recalculées | ✅ Cypress |
| NAV-06 | Téléchargement d'un fichier | Cliquer sur l'action télécharger | Le fichier se télécharge intégralement, barre de progression cohérente pendant le transfert | ✅ `FileControllerTest` |
| NAV-07 | Lecture en continu d'une vidéo (streaming par plages HTTP) | Ouvrir la prévisualisation d'un fichier vidéo et avancer dans la lecture | La lecture démarre sans attendre le téléchargement complet, avance/retour fonctionnent (`HttpRange`) | 🔲 |

## 3. Upload et suivi des transferts

| ID | Scénario | Étapes | Résultat attendu | Couverture |
|---|---|---|---|---|
| UP-01 | Upload d'un fichier unique | Glisser-déposer un fichier dans la zone de dépôt | Le fichier apparaît dans l'arborescence à la fin du transfert, barre de progression affichée pendant l'upload | ✅ Cypress |
| UP-02 | Upload d'un dossier avec sous-dossiers | Glisser-déposer un dossier contenant des sous-dossiers | L'arborescence est recréée à l'identique côté serveur, chaque fichier remonté individuellement dans le plateau de transferts | ✅ Cypress |
| UP-03 | Upload multi-fichiers simultané | Sélectionner plusieurs fichiers d'un coup via le sélecteur de fichiers | Tous les fichiers apparaissent dans le plateau de transferts, chacun avec sa propre progression | 🔲 |
| UP-04 | Échec réseau pendant un upload | Interrompre la connexion pendant un transfert | L'échec est signalé dans le plateau de transferts, l'application reste utilisable | 🔲 |
| UP-05 | Upload d'un fichier dans un dossier sans droit `WRITE` | Tenter d'uploader dans un dossier où l'utilisateur n'a que `READ` | `403 Forbidden`, aucun fichier créé côté serveur | ✅ `FileControllerTest`, `FileSystemSecurityEvaluatorTest` |

## 4. Prévisualisation multimédia

| ID | Scénario | Étapes | Résultat attendu | Couverture |
|---|---|---|---|---|
| PREV-01 | Prévisualisation d'une image | Ouvrir un fichier de type image | La vignette/aperçu s'affiche dans la carte, sans téléchargement manuel | ✅ `store-element.spec.ts` (rendu du composant) |
| PREV-02 | Prévisualisation d'une vidéo | Ouvrir un fichier vidéo | Une vignette générée côté serveur (`FfmpegVideoThumbnailGenerator`) s'affiche | 🔲 |
| PREV-03 | Absence de prévisualisation pour un PDF | Ouvrir un fichier PDF | Aucune tentative de rendu dans un `<iframe>` (choix délibéré, non accessible) ; icône de type de fichier affichée à la place | ✅ `store-element.spec.ts` |
| PREV-04 | Échec de génération de la vignette | Prévisualiser un fichier dont la génération de vignette échoue côté serveur | Repli automatique sur l'icône générique du type de fichier, pas d'erreur bloquante affichée à l'utilisateur | ✅ `store-element.spec.ts`, `PreviewThumbnailGeneratorRegistryTest` |
| PREV-05 | Prévisualisation d'un fichier sans droit `READ` | Tenter d'ouvrir la prévisualisation d'un fichier appartenant à un autre utilisateur, sans permission | `403 Forbidden`, aucune donnée du fichier exposée | ✅ `FileControllerTest` |

## 5. Administration - Utilisateurs

| ID | Scénario | Étapes | Résultat attendu | Couverture |
|---|---|---|---|---|
| ADM-USR-01 | Accès au panneau d'administration réservé | Un utilisateur non-admin accède à `/admin` | Redirection/blocage par `admin-guard`, aucune donnée d'administration chargée | ✅ Cypress + `admin-guard.spec.ts` |
| ADM-USR-02 | Création d'un compte utilisateur avec mot de passe faible | Créer un utilisateur avec un mot de passe de moins de 8 caractères | `400 Bad Request`, message explicite, aucun compte créé | ✅ `UserServiceTest`, `PasswordPolicyTest` |
| ADM-USR-03 | Création d'un compte utilisateur | Renseigner nom d'utilisateur et mot de passe conforme, valider | Le compte est créé avec son propre rôle personnel et son propre dossier, apparaît dans la liste des utilisateurs | ✅ Cypress + `UserServiceTest` |
| ADM-USR-04 | Attribution de rôles à un utilisateur | Depuis la fiche utilisateur, cocher/décocher des rôles, enregistrer | Les rôles effectifs de l'utilisateur sont mis à jour, reflétés à sa prochaine connexion | 🔲 |
| ADM-USR-05 | Un admin ne peut pas retirer son propre rôle ADMIN | Un compte admin tente de se retirer à lui-même le rôle `ADMIN` | Opération refusée (`400`), le rôle reste en place | ✅ `UserServiceTest` |
| ADM-USR-06 | Réinitialisation du mot de passe d'un utilisateur | Depuis le panneau admin, définir un nouveau mot de passe pour un compte | Le mot de passe est mis à jour (haché), l'utilisateur peut se reconnecter avec | 🔲 |
| ADM-USR-07 | Suppression d'un utilisateur | Supprimer un compte depuis le panneau admin | Le compte disparaît de la liste ; le compte `admin` intégré ne peut pas être supprimé, un admin ne peut pas se supprimer lui-même | ✅ `UserServiceTest` |

## 6. Administration - Rôles

| ID | Scénario | Étapes | Résultat attendu | Couverture |
|---|---|---|---|---|
| ADM-ROLE-01 | Création d'un rôle | Depuis le panneau rôles, saisir un nom et sélectionner des permissions, enregistrer | Le nouveau rôle apparaît dans la liste, avec les permissions choisies | ✅ Cypress + `RoleServiceTest` |
| ADM-ROLE-02 | Modification d'un rôle existant | Modifier le nom ou les permissions d'un rôle, enregistrer | Les changements sont répercutés sur tous les utilisateurs porteurs de ce rôle | 🔲 |
| ADM-ROLE-03 | Suppression d'un rôle | Supprimer un rôle depuis le panneau | Le rôle disparaît de la liste et des utilisateurs qui le portaient | 🔲 |

## 7. Administration - Permissions

| ID | Scénario | Étapes | Résultat attendu | Couverture |
|---|---|---|---|---|
| ADM-PERM-01 | Création d'une permission | Depuis le panneau permissions, saisir un nom, enregistrer | La nouvelle permission est disponible pour être associée à un rôle | 🔲 |
| ADM-PERM-02 | Suppression d'une permission | Supprimer une permission existante | La permission n'est plus proposée à l'association ; les rôles qui la portaient sont mis à jour | 🔲 |

## 8. Contrôle d'accès et sécurité (RBAC)

| ID | Scénario | Étapes | Résultat attendu | Couverture |
|---|---|---|---|---|
| SEC-01 | Accès à un fichier sans permission `READ` | Un utilisateur B tente d'ouvrir un fichier appartenant à A, sans droit accordé | `403 Forbidden`, aucun contenu exposé | ✅ Cypress + `FileSystemSecurityEvaluatorTest` |
| SEC-02 | Héritage de permission depuis un dossier parent | Un utilisateur a un droit `READ` sur un dossier parent, sans droit explicite sur un sous-fichier | L'accès au sous-fichier est autorisé par héritage (résolution au plus proche ancêtre) | ✅ `FileSystemSecurityEvaluatorTest` |
| SEC-03 | Un compte `ADMIN` accède à toutes les ressources | Un compte porteur du rôle `ADMIN` ouvre un fichier appartenant à un autre utilisateur | Accès autorisé sans grant explicite | ✅ `FileSystemSecurityEvaluatorTest` |
| SEC-04 | Tentative de traversée de chemin | Fournir un nom de fichier contenant `../` lors d'un upload ou d'un accès | La résolution de chemin échoue ou reste confinée au répertoire de stockage, aucune écriture/lecture hors périmètre | ✅ `FileServiceTest` |

---

## Synthèse

- **28 scénarios** documentés sur les 8 user stories/domaines fonctionnels du prototype.
- **17 couverts automatiquement** (tests unitaires/intégration JUnit ou Vitest, et/ou spec Cypress), rejoués à chaque build via mon pipeline Jenkins (cf. `compte_rendu.md`, C2.1.2).
- **11 restent à rejouer manuellement** avant chaque montée de version majeure - principalement des parcours secondaires (déconnexion, modification/suppression de rôle ou permission, réinitialisation de mot de passe, échec réseau pendant un upload) qui n'ont pas encore de spec Cypress ou de test dédié.

Ces 11 scénarios non automatisés constituent ma prochaine priorité d'extension de la suite Cypress plutôt qu'un simple exercice de recette manuelle répétée à chaque version.
