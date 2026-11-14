"""Synthetic disposable v3 fixture. Never reads or changes a user's real database."""
import sqlite3
import sys

with sqlite3.connect(sys.argv[1]) as db:
    db.executescript("""
CREATE TABLE tariffs (id INTEGER NOT NULL PRIMARY KEY, picksRate REAL NOT NULL, weightRate REAL NOT NULL, volumeRate REAL NOT NULL, boxesRate REAL NOT NULL, hoursRate REAL NOT NULL);
CREATE TABLE shifts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, startDate TEXT NOT NULL, endDate TEXT, isActive INTEGER NOT NULL);
CREATE TABLE work_days (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, picks REAL, weight REAL, volume REAL, boxes REAL, hours REAL, picksRate REAL NOT NULL, weightRate REAL NOT NULL, volumeRate REAL NOT NULL, boxesRate REAL NOT NULL, hoursRate REAL NOT NULL, shiftId INTEGER);
INSERT INTO tariffs VALUES (1, 2.5, 0, 0, 0, 0);
INSERT INTO shifts VALUES (9, '01.10.2026', NULL, 1);
INSERT INTO work_days (id,date,picks,picksRate,weightRate,volumeRate,boxesRate,hoursRate,shiftId) VALUES (7,'2026-10-24',1.234,2.5,0,0,0,0,9);
PRAGMA user_version=3;
""")
