# NextJS Client Android

[![Build Android APK](https://github.com/YOUR_USERNAME/YOUR_REPO/actions/workflows/build-android.yml/badge.svg)](https://github.com/YOUR_USERNAME/YOUR_REPO/actions/workflows/build-android.yml)

Application Android native pour la gestion des produits et analyses commerciales, développée en Kotlin avec une architecture MVVM moderne.

## 📱 Fonctionnalités

### 🏠 Dashboard Principal (Overview)
- **Statistiques en temps réel** : Visualisation des KPIs principaux
- **Analyses multi-fournisseurs** : Support Anecoop et Solagora
- **Graphiques interactifs** : Tendances et performances
- **Animations fluides** : Compteurs animés et transitions Material Design 3

### 📊 Module Scamark
- **Gestion des produits** : Liste complète avec filtres avancés
- **Sélecteur de semaines** : Navigation temporelle intuitive
- **Détails produits** : Fiches détaillées avec images et descriptions
- **Décisions clients** : Suivi des choix et préférences
- **Indicateurs de performance** : Prix, volumes, tendances

### 🎨 Interface Utilisateur
- **Material Design 3** : Interface moderne et cohérente
- **Thème adaptatif** : Support des thèmes clair/sombre
- **Personnalisation fournisseur** : Couleurs et branding dynamiques
- **Navigation fluide** : Bottom navigation et transitions animées

### 🔐 Sécurité & Authentification
- **Firebase Auth** : Authentification sécurisée
- **Gestion des sessions** : Token et refresh automatique
- **Stockage sécurisé** : SharedPreferences chiffrées
- **Configuration réseau** : SSL/TLS et certificats

## 🛠 Architecture Technique

### Stack Technologique
- **Langage** : Kotlin
- **Architecture** : MVVM (Model-View-ViewModel)
- **UI** : View Binding, Material Components
- **Backend** : Firebase (Auth, Firestore, Storage)
- **Build** : Gradle 8.0+
- **Min SDK** : 24 (Android 7.0)
- **Target SDK** : 34 (Android 14)

### Bibliothèques Principales
```gradle
- AndroidX Core & AppCompat
- Material Design Components
- Navigation Component
- Firebase BOM
- Lifecycle & LiveData
- Coroutines
- Glide (images)
- FlexboxLayout
```

### Structure du Projet
```
app/
├── src/main/java/com/nextjsclient/android/
│   ├── data/
│   │   ├── models/        # Modèles de données
│   │   └── repository/    # Repository pattern
│   ├── ui/
│   │   ├── overview/      # Fragment Dashboard
│   │   └── scamark/       # Module Scamark
│   ├── utils/             # Utilitaires
│   ├── AuthActivity.kt    # Authentification
│   ├── MainActivity.kt    # Activité principale
│   └── SettingsActivity.kt # Paramètres
└── res/
    ├── layout/            # Layouts XML
    ├── drawable/          # Icônes et formes
    ├── values/            # Couleurs, strings, thèmes
    └── navigation/        # Graphe de navigation
```

## 🚀 Installation

### Prérequis
- Android Studio Arctic Fox ou plus récent
- JDK 17
- Android SDK 34
- Gradle 8.0+

### Compilation Locale

1. **Cloner le projet**
```bash
git clone https://github.com/YOUR_USERNAME/YOUR_REPO.git
cd NextJSClientAndroid
```

2. **Ouvrir dans Android Studio**
```bash
studio .
```

3. **Compiler l'APK**
```bash
./gradlew assembleDebug     # Version debug
./gradlew assembleRelease   # Version release
```

### Installation APK
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 🔄 CI/CD avec GitHub Actions

Le projet est configuré pour builder automatiquement via GitHub Actions :

- **Build automatique** sur push vers `main` ou `develop`
- **3 artifacts générés** :
  - `app-debug.apk` : Version de développement
  - `app-release-unsigned.apk` : Release non signée
  - `app-release-signed.apk` : Release signée (Play Store ready)

### Télécharger les APKs
1. Aller dans l'onglet **Actions**
2. Sélectionner un workflow réussi
3. Télécharger depuis **Artifacts**

## 📲 Déploiement

### Google Play Store
L'APK signé (`app-release-signed.apk`) est prêt pour :
- Upload direct sur Play Console
- Distribution en beta testing
- Publication en production

### Distribution Alternative
- Firebase App Distribution
- Installation directe (APK)
- MDM d'entreprise

## 🔧 Configuration

### Firebase
Pour activer Firebase, ajoutez `google-services.json` dans `app/` :
1. Créer un projet Firebase
2. Ajouter une app Android
3. Télécharger `google-services.json`
4. Placer dans `app/`

### Variables d'Environnement
Configuration dans `local.properties` :
```properties
sdk.dir=/path/to/Android/Sdk
```

### Signature APK
Pour la signature locale, voir `SETUP_SIGNING.md`

## 📊 Performances

- **Taille APK** : ~8-10 MB
- **Temps de démarrage** : <2s
- **Support offline** : Cache local
- **Optimisations** : ProGuard ready

## 🤝 Contribution

Les contributions sont bienvenues ! Pour contribuer :

1. Fork le projet
2. Créer une branche (`git checkout -b feature/AmazingFeature`)
3. Commit (`git commit -m 'Add AmazingFeature'`)
4. Push (`git push origin feature/AmazingFeature`)
5. Ouvrir une Pull Request

## 📝 Licence

Ce projet est privé et propriétaire. Tous droits réservés.

## 📞 Support

Pour toute question ou problème :
- Ouvrir une issue GitHub
- Contact : [votre-email@example.com]

## 🏗 Roadmap

### Version 1.1 (Prochaine)
- [ ] Mode hors ligne complet
- [ ] Export PDF des rapports
- [ ] Notifications push
- [ ] Widget homescreen

### Version 1.2
- [ ] Multi-langue (FR/EN/ES)
- [ ] Graphiques avancés
- [ ] Intégration API REST
- [ ] Tests unitaires

## 🙏 Remerciements

- Material Design pour les guidelines UI
- Firebase pour le backend
- La communauté Android pour les ressources

---

**NextJS Client Android** - Application de gestion commerciale moderne 🚀