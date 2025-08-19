# 🌙 Configuration des Builds Nightly Automatiques

Ce guide explique comment configurer des builds APK automatiques à chaque commit, comme Lawnchair Launcher.

## 🚀 Fonctionnement

### Déclenchement Automatique
Le workflow se déclenche automatiquement sur :
- **Push sur `main`** → Build + Release Nightly
- **Push sur `develop`** → Build uniquement  
- **Pull Requests vers `main`** → Build + Commentaire automatique

### Types de Builds
1. **Debug APK** : Version de développement avec logs
2. **Release APK (non signé)** : Version optimisée mais non signée
3. **Release APK (signé)** : Version prête pour distribution (si keystore configuré)

## 📦 Release Nightly

### Comportement comme Lawnchair
- Chaque commit sur `main` crée une **nouvelle release nightly**
- L'ancienne release nightly est **automatiquement remplacée**
- Tag `nightly` toujours mis à jour avec la dernière version
- Changelog automatique avec les 10 derniers commits
- Marquée comme **prerelease** pour indiquer l'instabilité

### Format de Version
- **Nightly** : `nightly-20241219-a1b2c3d`
- **Release** : `v1.0.0` (sur tags manuels)

## 🔐 Configuration des Secrets (Optionnel)

### 1. Firebase (Google Services)

```bash
# Encoder votre google-services.json
base64 -i NextJSClientAndroid/app/google-services.json | pbcopy
```

**Créer le secret :** `GOOGLE_SERVICES_JSON`

### 2. Signature APK (Recommandé)

#### Génerer un keystore
```bash
keytool -genkey -v -keystore app-release.keystore \
  -alias nextjs-client -keyalg RSA -keysize 2048 -validity 10000
```

#### Encoder le keystore
```bash
base64 -i app-release.keystore | pbcopy
```

#### Secrets à créer
- `KEYSTORE_BASE64` : Keystore encodé en base64
- `KEYSTORE_PASSWORD` : Mot de passe du keystore
- `KEY_ALIAS` : Alias de la clé (ex: nextjs-client)
- `KEY_PASSWORD` : Mot de passe de la clé

## 🎯 Utilisation

### Téléchargement des APKs

#### Depuis les Actions (tous les builds)
1. Aller dans **Actions** sur GitHub
2. Cliquer sur le workflow terminé
3. Télécharger dans **Artifacts**

#### Depuis les Releases (nightly uniquement)
1. Aller dans **Releases**
2. Cliquer sur **nightly**
3. Télécharger l'APK souhaité

### Déclencher manuellement
1. Aller dans **Actions**
2. Sélectionner **Build Android APK**
3. Cliquer sur **Run workflow**

## 🔄 Intégration avec le Système de Mise à Jour

Le système de mise à jour de l'application va automatiquement :
- Détecter les nouvelles versions nightly
- Proposer le téléchargement
- Afficher les changelogs

### Configuration nécessaire
Mettre à jour dans `/app/api/update/check/route.ts` :
```typescript
const GITHUB_REPO = 'votre-username/votre-repo'
```

## 📱 Installation des APKs

### Sur Android
1. Télécharger l'APK depuis GitHub
2. Activer **Sources inconnues** dans les paramètres
3. Installer l'APK téléchargé

### Choix de l'APK
- **Debug** : Pour les développeurs (logs détaillés)
- **Release unsigned** : Version optimisée, compatible tous appareils
- **Release signed** : Version optimisée et sécurisée (recommandée)

## 🛠️ Personnalisation

### Modifier les branches de build
Dans `.github/workflows/build-android.yml` :
```yaml
on:
  push:
    branches: [ main, develop, votre-branche ]
```

### Changer la fréquence de nettoyage
```yaml
retention-days: 90  # Garder 90 jours au lieu de 30
```

### Ajouter des variantes de build
```yaml
- name: Build Staging APK
  run: ./gradlew assembleStaging
```

## 🆘 Dépannage

### Build échoue
- Vérifier que `gradlew` est exécutable
- Vérifier les erreurs dans les logs Actions
- Tester le build localement

### APK signé non créé
- Vérifier que tous les secrets de keystore sont configurés
- Vérifier les mots de passe dans les secrets

### Release nightly non créée
- Vérifier les permissions `contents: write`
- S'assurer que le push est sur la branche `main`

## 🎉 Résultat

Après configuration, vous obtiendrez :
- ✅ **Builds automatiques** à chaque commit
- ✅ **Releases nightly** comme Lawnchair
- ✅ **APKs signés** prêts pour distribution
- ✅ **Système de mise à jour** intégré dans l'app
- ✅ **Commentaires automatiques** sur les PRs
- ✅ **Changelog automatique** dans les releases

---

🤖 **Système inspiré de Lawnchair Launcher** pour une expérience de développement optimale !