# Multi-threaded File Download — Client/Server (Java)

Files: `Server.java`, `Client.java`. Needs Java 11+. No libraries.

## Compile

```bash
javac -d out Server.java Client.java
```

## Run

Put the file you want to download into a folder named `shared` (next to where you run the commands).

```bash
# Terminal 1 — server        (mode: trad = Traditional I/O, nio = NIO transferTo)
java -cp out Server nio

# Terminal 2 — client
java -cp out Client list
java -cp out Client info test.bin
java -cp out Client download test.bin 10 nio 3
#                            file     workers mode runs
```

The downloaded file is saved in the `downloads` folder.
Choose the **same mode** on the server and the client (`trad` with `trad`, `nio` with `nio`).

## Choose the write method

In `Client.java`, inside `download(...)`, find **CHANGE HERE** and keep ONE line (change the method name), then recompile:

```java
writePartFilesThenMerge(name, size, workers, nio);
// writeToFinalFile(name, size, workers, nio);
```

- `writePartFilesThenMerge` — each worker writes its own part file, then the parts are merged into the final file.
- `writeToFinalFile` — the final file is made full size, each worker uses its own `RandomAccessFile` and writes at its own offset.

## Check size / hash

The client checks the size itself and prints the SHA-256 of the downloaded file. Compare it with the original:

```bash
sha256sum shared/test.bin                  # Linux / macOS
certutil -hashfile shared\test.bin SHA256  # Windows
```

## Requirement checklist

| Requirement in the PDF | Where it is |
|---|---|
| TCP, command line | `Server`, `Client` |
| Server handles many connections at once | `Server.main` — thread pool (`newFixedThreadPool`) |
| 10 parts, 10 workers, each with own connection / offset / length, no overlap, last worker takes the rest | `Client.offsetOf`, `Client.lengthOf`, `runAll`, `fetchRange` |
| Write as part files + merge, or into the final file by offset (own handle per worker) | `writePartFilesThenMerge`, `writeToFinalFile` |
| Protocol `LIST`, `INFO`, `GET`, `ERROR` | `Server.handle` (and `list`, `info`, `get`) |
| Steps: ask size → compute ranges → workers `GET` → merge/confirm → check size/hash | `Client.download` |
| Server reads only the requested range | `Server.get` |
| Traditional I/O vs NIO | `trad`: `InputStream.read` / `OutputStream.write` — `nio`: `transferTo` (server) / `transferFrom` (client) |
| Test 1 and 10 workers, ≥ 3 runs, time, MB/s, size/hash | `Client download <file> <workers> <mode> <runs>` |

## Experiment

Same file, same machine. For **each write method**, run these 4 cases (3 runs each) and fill in your own numbers:

```bash
java -cp out Server trad        # then:  java -cp out Client download test.bin 1  trad 3
                                #        java -cp out Client download test.bin 10 trad 3
java -cp out Server nio         # then:  java -cp out Client download test.bin 1  nio 3
                                #        java -cp out Client download test.bin 10 nio 3
```

| Write method | Mode | Workers | Run 1 (s / MB/s) | Run 2 | Run 3 | Size / hash |
|---|---|---|---|---|---|---|
| | Traditional | 1 | | | | |
| | Traditional | 10 | | | | |
| | NIO | 1 | | | | |
| | NIO | 10 | | | | |

(MB/s = file size in MB ÷ total time; 1 MB = 1,048,576 bytes. Total time includes the merge when you use `writePartFilesThenMerge`.)

## Notes for the report (อธิบายผล)

**ทำไม 10 workers ไม่เร็วขึ้น 10 เท่า**
- ทุก worker ใช้ทรัพยากรชุดเดียวกัน (CPU, memory, loopback/เครือข่าย, ดิสก์) การเพิ่ม connection ไม่ได้ทำให้คอขวดใหญ่ขึ้น
- มี overhead เพิ่ม เช่น เปิด 10 connection/threads, context switch, เขียนดิสก์หลายตำแหน่งพร้อมกัน
- งานบางส่วนทำพร้อมกันไม่ได้ เช่น ขอ INFO และการ merge part files เวลารวมจึงขึ้นกับ worker ที่ช้าที่สุด
- การแบ่งช่วงช่วยมากเมื่อคอขวดคือความเร็วต่อ connection (เครือข่ายไกล) ไม่ใช่แบนด์วิดท์รวมของเครื่อง

**ทำไม NIO อาจไม่ชนะทุกครั้ง**
- `transferTo` ลดการ copy ข้อมูลระหว่าง kernel กับโปรแกรมได้ แต่บน localhost ข้อมูลอยู่ใน memory อยู่แล้ว ต้นทุนที่ประหยัดจึงน้อยมาก
- ฝั่ง client `transferFrom` จาก socket ใน JDK ทั่วไปยังอ่านผ่าน buffer ภายใน จึงได้ประโยชน์ไม่เท่าฝั่ง server
- เวลาที่วัดแกว่งในแต่ละรอบ (JIT, GC, scheduler, cache) ทำให้ผลสลับกันชนะได้

**ข้อจำกัดของการทดสอบผ่าน localhost**
- *Page Cache*: ไฟล์ต้นทางอยู่ใน RAM หลังอ่านครั้งแรก และไฟล์ปลายทางถูกเขียนลง cache ก่อน ตัวเลขจึงสูงกว่าการอ่าน/เขียนดิสก์จริง
- *Loopback network*: ไม่ผ่านการ์ดเครือข่ายจริง ไม่มี latency หรือ bandwidth จำกัด และ client กับ server แย่ง CPU เครื่องเดียวกัน
- *Storage cache*: read-ahead / write-back ของ OS และ cache ของ SSD ทำให้เวลาเขียนดูเร็วกว่าความเป็นจริง
