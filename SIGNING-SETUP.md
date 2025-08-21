# Configuration de Signature APK

## Problème

Les APK générées par GitHub Actions changent de signature à chaque build, empêchant les mises à jour automatiques (erreur "package invalide").

## Solution

Créer un keystore de production stable et le configurer dans GitHub Actions.

## Étapes

### 1. Créer le keystore de production

```bash
./create-keystore.sh
```

Ce script va :
- Créer `nextjs-client-release.keystore` avec des mots de passe sécurisés
- Créer `keystore-passwords.txt` avec les informations nécessaires
- Afficher la signature du certificat

### 2. Préparer les secrets GitHub

```bash
./prepare-github-secrets.sh
```

Ce script va afficher les secrets à configurer dans GitHub.

### 3. Configurer GitHub Actions

1. Allez dans **Settings → Secrets and variables → Actions**
2. Ajoutez les secrets affichés par le script précédent :
   - `KEYSTORE_BASE64` : Le keystore encodé en base64
   - `KEYSTORE_PASSWORD` : Mot de passe du keystore
   - `KEY_ALIAS` : Alias de la clé (nextjs-client)
   - `KEY_PASSWORD` : Mot de passe de la clé

### 4. Vérifier la configuration

Le prochain push sur `main` utilisera le keystore de production et aura une signature stable.

## Fichiers à ne PAS commiter

Ajoutez au `.gitignore` :
```
*.keystore
*.jks
keystore.properties
keystore-passwords.txt
```

## Vérification

Pour vérifier la signature d'une APK :
```bash
apksigner verify --verbose --print-certs app.apk
```

## Important

- Le keystore doit être **créé une seule fois** et réutilisé
- **Gardez les mots de passe en sécurité** (keystore-passwords.txt)
- **Ne commitez jamais** le keystore ou les mots de passe
- Toutes les APK signées avec le même keystore sont **compatibles** pour les mises à jour

## Test de stabilité signature

Signature APK run95: SHA-256 `3cc8a75b54faa695489a4211192cf11a7d4a5941344c6abc8ddd72650a1f980d`

## Dépannage

### "Package invalide" lors de l'installation
- Vérifiez que les secrets GitHub sont configurés
- Vérifiez que le keystore est correctement utilisé dans le workflow
- Désinstallez l'ancienne version si elle a une signature différente

### Keystore non trouvé dans GitHub Actions
- Vérifiez que `KEYSTORE_BASE64` est configuré dans les secrets
- Vérifiez que le keystore se décode correctement (voir logs du workflow)