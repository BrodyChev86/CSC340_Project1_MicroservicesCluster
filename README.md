# CSC340_Project1_MicroservicesCluster
A distributed networking system that enables clients to submit computational tasks to a dynamic pool of worker nodes without prior knowledge of their locations. The system mirrors real-world service-oriented and microservice architectures by separating a control plane and a data plane.

## Overview
This project consists of several Java components organized into folders:

- `ServerClientTools/` – contains the server, client, and routing logic for handling tasks and communicating with worker nodes.
- `Base64EncodeDecode/` – contains the Base64 encoder and decoder service node as well as test files.
- `CSV_Stats/` – contains the CSV Stats service node as well as test files.
- `FileEntropyAnalyzer/` – contains the File Entropy Anaylizer node as well as test files.
- `ImageTransformer/` – contains the Image Transformer service node.

Each component can be compiled and run independently, but the core microservices cluster functionality lives under `ServerClientTools`.

## Getting Started
To run the project, follow these simple steps:

1. **Compile the Java code**
   ```powershell
   cd ServerClientTools
   javac *.java
   ```
   You can compile other sub-projects in the same way by changing into their directories.

2. **Start the Server**
   ```powershell
   java Server
   ```
   The server listens for incoming client requests and manages worker nodes.

3. **Launch Clients or Worker Nodes**
   In separate terminals, start the `Client` program to send tasks, or run a `ServiceNode` to act as a worker.

   ```powershell
   java Client
   # or
   java Base64
   # or
   java CSV_Reader
   # or
   java ImageTransformer
   # or
   java EntropyNode
   ```

   Workers register with the server and await tasks.

## Configuration – Localhost IP Addresses
All network connections are configured to communicate with `localhost` (127.0.0.1) by default. If you need to run the components on different machines, you must update the hardcoded IP addresses in the source files before compiling. Look for lines similar to:

```java
String serverHost = "127.0.0.1";  // change this to the server's IP
InetAddress.getByName("localhost") //change this to the server's IP
```

and replace `127.0.0.1` or `localhost` with the appropriate host address or hostname for your environment. The common files to edit include:

- `ServerClientTools/Client.java`
- `ServerClientTools/Server.java`
- `ServerClientTools/EntropyNode.java`
- `ImageTransformer/ImageTransformer.java`
- `CSV_Stats/CSV_Reader.java`
- `Base64EncodeDecode/Base64.java`

After editing the addresses, recompile the affected classes.

## Usage Tips
- Ensure the server is running before starting clients or workers.
- Use consistent port numbers if modifying them (default is visible in the source).
- For testing, you can run all components on the same machine using different terminals.

## Client Commands to the Server

The client application behaves like a chat program with several special commands that trigger server-side services.  Any unrecognized text is broadcast as a regular chat message.  Commands are case‑sensitive and trim the username prefix automatically.

- **upload** – Opens a file chooser. Selected files are sent to the server and stored as the "current" file.
- **BASE64 ENCODE_FILE** – Ask a available Base64 node to encode the current file; result is downloaded as a text file.
- **BASE64 DECODE_FILE** – Request a Base64 node to decode the current file; output is returned with the original name/extension.
- **BASE64 ENCODE_TEXT <text>** – Send raw text to a node and receive its base‑64 encoding.
- **BASE64 DECODE_TEXT <text>** – Send base‑64 text to a node and receive the decoded string.
- **ENTROPY <file>** – Sends the current file to an entropy service node; the file name is ignored, and the numeric entropy result is returned.
- **CSV <file>** - Sends a .csv file to the node and prints the statistics to the consol.
- **NODE_LIST** – Displays all connected service nodes and their offered services.
- **list** – Redisplay this command menu.
- **exit** – Disconnect the client from the server.

Most of these commands require the corresponding service node to be running and registered with the server.
