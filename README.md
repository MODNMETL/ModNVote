# ModNVote

**Modern, transparent community voting for PaperMC 1.21.x**

A privacy-first, lightweight, and production-ready **yes/no voting plugin** for Minecraft servers — designed for modern communities that value fairness, trust, and data integrity.  
ModNVote is built by [**MODN METL LTD**](https://modnmetl.com) and open-sourced to promote transparent community decision-making.

![CI](https://github.com/MODNMETL/ModNVote/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-007396)
![Paper](https://img.shields.io/badge/Paper-1.21.x-blue)
![License: MIT](https://img.shields.io/badge/License-MIT-green)
![Release](https://img.shields.io/github/v/release/MODNMETL/ModNVote?display_name=tag)

---

## ✨ Features

- `/modnvote yes` or `/modnvote no` — anonymous voting (1 per UUID).  
- `/modnvote status` — displays live YES/NO tallies.  
- `/modnvote verify` — cryptographically validates vote integrity.  
- `/modnvote audit` — shows how many votes came from bypass-permitted users.  
- `/modnvote fullaudit` — groups voters by IP to check for irregularities (no vote disclosure).  
- `/modnvote reset` — admin command to clear all votes.  
- `/modnvote reload` — reloads configuration without restarting the server.  
- **IP-based duplicate prevention** with configurable bypass permission.  
- **SQLite persistence** for reliability and restart safety.  
- **Integrity hashing** ensures tallies cannot be silently altered.  
- **PlaceholderAPI support** for scoreboard and UI integration.  

> ModNVote is a successor to the PineVote plugin, rebuilt for broader use under the MODN METL brand.

---

## 🧭 Roadmap

- [ ] MySQL database support for large networks  
- [ ] Configurable voting periods with automatic start/end  
- [ ] Player login reminders about active votes  
- [ ] Admin confirmation before vote resets  
- [ ] `/modnvote stop` command to end voting early (without wiping results)  
- [ ] Multi-question polls and rich voting UIs  

Want to contribute ideas or code? Open an issue or pull request!

---

## 🤝 Contributing

Contributions are welcome and encouraged.  
Please fork the repository and submit a pull request via GitHub — following standard Java + Paper plugin best practices.

Before submitting, ensure your code:
- Passes `gradlew build` without warnings or errors  
- Uses modern Java 21 syntax and adheres to the existing style  
- Includes concise comments for non-trivial logic  

---

## 🔐 Security

If you discover a vulnerability, please **do not** post it publicly.  
Instead, email the maintainers at **security@modnmetl.com**.  
Responsible disclosure ensures fixes can be deployed before details are released.

---

## 📜 License

This project is licensed under the **MIT License** — see the [LICENSE](./LICENSE) file for details.  
You are free to use, modify, and distribute this software with attribution.

```
Copyright (c) 2025 MODN METL LTD
Developed by Jamie Edward Thompson (@jamjet3)
```

---

## 🏗️ Credits

- **Development Lead:** [Jamie Edward Thompson](https://github.com/jamjet3)  
- **Maintainer:** [MODN METL LTD](https://github.com/MODNMETL)  
- **Community Testing:** Pinecraft Equestrian SMP  
- **Build System:** Gradle + ShadowJar  
- **Supported Platforms:** PaperMC 1.21.x  

---

> “Trust, but verify.” — The guiding principle behind **ModNVote**  
> Built to help communities make fair, transparent decisions — the modern way.
