# WhatsApp Auto Answer - Ghid Complet

## Ce face aceasta aplicatie?
Raspunde automat la apelurile WhatsApp (VIDEO sau AUDIO) fara sa atingi ecranul.
Functioneaza prin Android Accessibility Service - serviciul "vede" ecranul si apasa 
automat butonul de raspuns.

---

## Cerinte
- Android 8.0 sau mai nou (Huawei EMUI 8+ / HarmonyOS)
- WhatsApp sau WhatsApp Business instalat
- Android Studio (pentru compilare)

---

## Cum compilezi APK-ul

### Pas 1 - Instaleaza Android Studio
https://developer.android.com/studio

### Pas 2 - Deschide proiectul
File → Open → selecteaza folderul `WhatsAppAutoAnswer`

### Pas 3 - Compileaza
Build → Build Bundle(s) / APK(s) → Build APK(s)

APK-ul se gaseste in:
`app/build/outputs/apk/debug/app-debug.apk`

### Pas 4 - Transfera pe tableta Huawei
- Prin USB (Android File Transfer)
- Sau email/cloud catre tine

### Pas 5 - Instaleaza pe Huawei
Pe tableta:
1. Setari → Securitate → Surse necunoscute → Activeaza
2. Deschide fisierul APK
3. Instaleaza

---

## Configurare pe tableta Huawei (IMPORTANT!)

### 1. Activeaza Accessibility Service
```
Setari → Accesibilitate → Servicii instalate → WhatsApp Auto Answer → ON
```

### 2. Pe EMUI/HarmonyOS (Huawei specific):
```
Setari → Baterie → Lansare aplicatii → WhatsApp Auto Answer
→ Dezactiveaza "Gestionare automata"
→ Activeaza: Pornire automata, Rulare in fundal, Pornire indirecta
```

### 3. Permisiuni suplimentare Huawei:
```
Setari → Aplicatii → WhatsApp Auto Answer → Permisiuni
→ Asigura-te ca are toate permisiunile
```

---

## Cum functioneaza tehnic

1. **AccessibilityService** monitorizeaza evenimentele WhatsApp
2. Cand detecteaza un apel incoming (prin ID butoane sau text pe ecran)
3. Asteapta delay-ul configurat (1-5 secunde)
4. Apasa butonul "Raspunde video" sau "Raspunde audio"
5. Fallback: daca nu gaseste butonul, face tap gestural pe pozitia aproximativa

### Butoane detectate automat:
- `com.whatsapp:id/answer_video_call_btn`
- `com.whatsapp:id/video_call_btn`
- `com.whatsapp:id/answer_call_btn`
- (si echivalentele pentru WhatsApp Business)

---

## Depanare

### Serviciul nu porneste?
→ Verifica Accessibility Settings → reporneste tableta

### Nu raspunde la apeluri?
→ Verifica ca WhatsApp e deschis cel putin o data dupa boot
→ Pe Huawei: dezactiveaza optimizarea bateriei pentru aplicatie
→ Mareste delay-ul la 3-4 secunde din aplicatie

### Raspunde audio in loc de video?
→ Normal - daca apelul e audio, nu exista buton video
→ Daca apelul e video dar raspunde audio: versiunea WhatsApp poate fi diferita
   (ID-urile butoanelor se schimba cu update-urile)

### Update WhatsApp schimba ID-urile butoanelor?
Modifica in `WhatsAppCallService.java`:
```java
private static final String[] VIDEO_BUTTON_IDS = {
    "com.whatsapp:id/NOUL_ID_BUTON",
    ...
};
```
Poti gasi ID-urile noi cu Android Studio → Layout Inspector

---

## Structura proiect

```
WhatsAppAutoAnswer/
├── app/
│   ├── src/main/
│   │   ├── java/com/autoanswerwa/app/
│   │   │   ├── MainActivity.java          ← UI configurare
│   │   │   ├── WhatsAppCallService.java   ← LOGICA PRINCIPALA
│   │   │   └── BootReceiver.java          ← Pornire automata
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   ├── values/strings.xml
│   │   │   └── xml/accessibility_service_config.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle
└── README.md
```
