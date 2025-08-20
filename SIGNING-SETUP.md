# Configuration de la signature APK

## Configuration réalisée

L'application est maintenant configurée pour signer automatiquement les APK avec un certificat de production stable, comme le fait Lawnchair.

### 1. Keystore créé
- Un keystore de release a été généré avec un certificat valide 10 000 jours
- Stocké dans `nextjs-client-release.keystore`
- Alias : `nextjs-client`

### 2. Build.gradle configuré
- Signature automatique pour les builds release
- Support pour GitHub Actions (variables d'environnement)
- Fallback sur le keystore local si disponible

### 3. GitHub Actions configuré
- Workflow `.github/workflows/build-release.yml`
- Build automatique sur push/tag
- Upload des APK signés comme artifacts
- Création automatique de releases pour les tags

## Configuration des secrets GitHub

**IMPORTANT** : Vous devez ajouter ces secrets dans votre repo GitHub :

1. Allez sur : `https://github.com/[votre-username]/[votre-repo]/settings/secrets/actions`

2. Exécutez `./prepare-github-secrets.sh` pour obtenir les valeurs

3. Ajoutez ces 4 secrets :
   - `KEYSTORE_BASE64` : Le keystore encodé en base64
   - `KEYSTORE_PASSWORD` : Le mot de passe du keystore
   - `KEY_ALIAS` : `nextjs-client`
   - `KEY_PASSWORD` : Le mot de passe de la clé (identique au keystore)

## Pourquoi Play Protect ne se déclenche plus

Avec cette configuration :
- L'APK est signé avec un certificat de production stable
- Le même certificat est utilisé pour toutes les versions
- Google Play Protect reconnaît la signature cohérente
- L'application établit une "réputation" au fil du temps

## Build local

Pour construire un APK signé localement :
```bash
./gradlew assembleRelease
```

L'APK signé sera dans : `app/build/outputs/apk/release/app-release.apk`

## Vérification de la signature

Pour vérifier qu'un APK est bien signé :
```bash
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

## Sécurité

- **NE JAMAIS** commiter le fichier `keystore-passwords.txt`
- **NE JAMAIS** commiter le fichier `nextjs-client-release.keystore` 
- Conservez une copie sécurisée du keystore et des mots de passe
- Sans le keystore original, vous ne pourrez plus mettre à jour l'app