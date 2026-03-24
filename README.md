# CSC340_Project1_MicroservicesCluster
A distributed networking system that enables clients to submit computational tasks to a dynamic pool of worker nodes without prior knowledge of their locations. The system mirrors real‑world service‑oriented and microservice architectures by separating a control plane (the server) and a data plane (service nodes).

## Overview
This workspace is organized by service type. Each folder contains a node implementation that can register with the server and perform a specific operation on behalf of clients.

- **ServerClientTools/** – the core cluster logic: the server, client, node handlers, and shared utilities.  You will normally start the server from here and run clients and service nodes that depend on it.
- **Base64EncodeDecode/** – a node that encodes/decodes text or files in Base64.  Contains `Base64.java` and test resources.
- **CSV_Stats/** – computes simple statistics on CSV files.  The node is implemented in `CSV_Reader.java`.
- **FileEntropyAnalyzer/** – accepts any uploaded file and returns its Shannon entropy.  Implementation lives in `EntropyNode.java` along with supporting analyzer classes.
- **ImageTransformer/** – performs basic image operations (rotate, resize, grayscale) on PNG/JPG data.  The class is `ImageTransformer.java` which can run either as a client utility or as a service node.
- **TOPKTerms/** – finds the most frequent terms in text; the service node is `TOPK.java`.

Each subproject can be compiled on its own; the cluster behaviour is driven by code under `ServerClientTools`.

## Getting Started
Use a terminal for each component you wish to run.  All commands assume you are in the workspace root unless noted otherwise.

1. **Compile everything** (or just individual packages):
   ```powershell
   cd ServerClientTools
   javac *.java
   cd ..\Base64EncodeDecode
   javac Base64.java
   cd ..\CSV_Stats
   javac CSV_Reader.java
   # …repeat for other packages as needed
   ```
   Each service node has a `main` method; compilation is required before running.

2. **Start the server** (in `ServerClientTools`):
   ```powershell
   cd ServerClientTools
   java Server
   ```
   The server listens on TCP port 1234 for clients and nodes, and UDP port 1235 for heartbeats.

3. **Launch clients or nodes** in separate terminals:
   ```powershell
   # client that users interact with:
   javac Client.java          # compile if not already
   java Client

   # worker/service nodes (choose one per terminal):
   java Base64EncodeDecode.Base64
   java CSV_Stats.CSV_Reader
   java FileEntropyAnalyzer.EntropyNode
   java ImageTransformer.ImageTransformer
   java TOPKTerms.TOPK
   ```
   Each node will print a hello message and send periodic UDP heartbeats to the server.

4. **Upload files and issue commands** using the client.  See the “Client Commands” section below for valid syntax.

### Configuration – Localhost and Ports
All network connections default to `localhost` (127.0.0.1) and ports 1234 (TCP) / 1235 (UDP).  To run components across machines, edit the  `config.properties` file to change the IP and Port #'s for the services listed below, then recompile:

- `ServerClientTools/Client.java`
- `ServerClientTools/Server.java`
- `ServerClientTools/EntropyNode.java`
- `ImageTransformer/ImageTransformer.java`
- `CSV_Stats/CSV_Reader.java`
- `Base64EncodeDecode/Base64.java`

Do **not** mix ports between clients and nodes; the server expects the defaults unless you adjust both sides.

### Usage Tips
- Always start the server first; clients or nodes started beforehand will fail to connect.
- If you change source code, recompile the modified class before rerunning.
- Testing on one machine is easiest; open multiple terminal windows and start each component there.
- Logs printed to the console indicate connections, requests, and heartbeats.

## Client Commands
The client interface resembles a chat window.  Most messages are forwarded to the server which interprets them as commands; anything not recognized is broadcast as chat.

Commands are **case‑sensitive** and the username prefix (e.g. `user: `) is stripped automatically.

| Command syntax | Description | Requirements |
|----------------|-------------|--------------|
| `upload` | Open file chooser and send the selected file to the server. | None; must run before file‑based services. |
| `list` | Show available commands again. | – |
| `exit` | Disconnect from server. | – |
| `NODE_LIST` | Show all connected service nodes and the services they offer. | Server must be running. |
| `BASE64 ENCODE_FILE` | Ask a Base64 node to encode the currently uploaded file; response downloaded as `.txt`. | A file must be uploaded. |
| `BASE64 DECODE_FILE [ext]` | Ask a Base64 node to decode the uploaded file’s contents; optional output extension. | File uploaded; output extension defaults to original or `bin`. |
| `BASE64 ENCODE_TEXT <text>` | Send text to a Base64 node and get its encoded form. | Node must be connected. |
| `BASE64 DECODE_TEXT <text>` | Send Base64 text to decode. | Node must be connected. |
| `ENTROPY` | Compute entropy of the uploaded file. | File uploaded. |
| `CSV` | Analyze the uploaded CSV file and print statistics. | File uploaded with `.csv` extension. |
| `TOPK FILE [k]` | Find top‑k terms in uploaded `.txt` file (default k = 3). | File uploaded with `.txt` extension. |
| `TOPK [k] <text>` | Find top‑k terms in provided text string (default k = 3). | Node must be connected. |
| `IMGT ROTATE <degrees>` | Rotate uploaded image by given degrees. | File uploaded (`.png` or `.jpg`) and image node available. |
| `IMGT RESIZE <width> <height>` | Resize uploaded image. | File uploaded (`.png` or `.jpg`) and image node available. |
| `IMGT TOGRAYSCALE` | Convert uploaded image to grayscale. | Same as rotate/resize. |

> ⚠️ Many commands require the corresponding service node to be running.  Use `NODE_LIST` to verify.

Feel free to explore by combining uploads with different service requests. Enjoy the microservices cluster!  
