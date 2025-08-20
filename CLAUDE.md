# Instructions pour Claude - Projet NextJSClientAndroid

## Configuration Git et Commits

### Messages de commit
- **NE JAMAIS** ajouter ces lignes dans les messages de commit :
  ```
  🤖 Generated with [Claude Code](https://claude.ai/code)
  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- Utiliser des messages de commit simples et professionnels
- Format préféré : `type: description courte et claire`
- Types : feat, fix, docs, style, refactor, test, chore

### Exemple de commit correct
```bash
git commit -m "feat: Ajoute la signature APK de production avec certificat stable"
```

## Configuration de signature APK

Le projet utilise un système de signature APK de production pour éviter les alertes Play Protect :

- **Keystore** : `nextjs-client-release.keystore` (NE JAMAIS commiter)
- **Mots de passe** : Stockés dans `keystore.properties` (NE JAMAIS commiter)
- **GitHub Actions** : Configure les secrets KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
- **Documentation** : Voir `SIGNING-SETUP.md`

## Fichiers sensibles à ne JAMAIS commiter

- `*.keystore`
- `*.jks`
- `keystore.properties`
- `keystore-passwords.txt`
- Tout fichier contenant des mots de passe ou clés

## Standards de code

### Kotlin
- Suivre les conventions Kotlin existantes dans le projet
- Utiliser les data classes pour les modèles
- Préférer les fonctions d'extension quand approprié

### Build configuration
- Les mots de passe doivent toujours être dans des fichiers de propriétés séparés
- Utiliser des variables d'environnement pour CI/CD
- Toujours avoir un fallback pour les configurations locales

## Commandes utiles

### Build
```bash
# Build debug
./gradlew assembleDebug

# Build release signé
./gradlew assembleRelease

# Vérifier la signature
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

### Tests
```bash
# Lancer les tests
./gradlew test

# Lancer les lints
./gradlew lint
```

## Architecture du projet

- **MainActivity** : Point d'entrée de l'application
- **Fragments** : Navigation par fragments (Overview, Scamark, etc.)
- **Repository** : FirebaseRepository pour la gestion des données
- **ViewModels** : Architecture MVVM
- **UpdateManager** : Gestion des mises à jour depuis GitHub

## Notes importantes

1. Le projet utilise Firebase mais avec un fallback hors ligne
2. Les images produits sont chargées depuis Firebase Storage
3. Le système de mise à jour vérifie les releases GitHub
4. L'APK doit toujours être signé avec le même certificat pour maintenir la confiance

## Préférences utilisateur

- Messages courts et directs
- Pas d'emojis dans le code ou les commits
- Documentation en français
- Code en anglais (noms de variables, fonctions, etc.)