# Legacy Swing app

The original version of this project: one Java class, one text file, no build tool.

## What it did

A desktop slang dictionary with search (by word and by definition), add, edit, delete, reset, a random word, and a four-option quiz. State lived in a `HashMap<String, String>`; every change rewrote `slang.txt` in full.

## Running it

```bash
javac SlangDictionaryApp.java
java SlangDictionaryApp
```

It must run from this directory — the file path `slang.txt` is relative and hard-coded.

## Data format

One entry per line, word and definition separated by a backtick:

```
BBC`British Broadcasting Corporation
JCB`J C Bamford | excavator manufacturer
```

7,641 entries. The same file seeds the PostgreSQL database in the current version, so the two share their data exactly.

## What changed, and why

The Swing app is fine as coursework and wrong as software anyone else would use: one user at a time, no server, a full file rewrite on every edit, search history lost on exit, and no way to test any of it — the business rules are tangled into `JOptionPane` calls.

The rewrite in [`../backend`](../backend) and [`../frontend`](../frontend) keeps every feature and moves the rules into services that can be tested without a UI. This directory stays unchanged so the two can be compared.
