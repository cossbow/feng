import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import * as os from 'os';
import * as https from 'https';
import * as cp from 'child_process';
import { Trace } from 'vscode-jsonrpc';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    StreamInfo,
} from 'vscode-languageclient/node';

const GITHUB_REPO = 'cossbow/feng';
const SERVER_CLASS = 'org.cossbow.feng.lsp.FengLspMain';

let client: LanguageClient | undefined;

export async function activate(context: vscode.ExtensionContext): Promise<void> {
    const output = vscode.window.createOutputChannel('Fēng Language Server');

    // Resolve the server JAR (explicit setting, cache, or GitHub Releases).
    let jarPath: string;
    try {
        jarPath = await resolveServerJar(context, output);
    } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        output.appendLine(`[error] ${msg}`);
        void vscode.window.showErrorMessage(
            `Fēng: failed to locate the language server. ${msg}`);
        return;
    }

    const javaPath = vscode.workspace
        .getConfiguration('feng.lsp').get<string>('javaPath', 'java');

    const serverOptions: ServerOptions = async (): Promise<StreamInfo> => {
        const proc = cp.spawn(javaPath, ['-cp', jarPath, SERVER_CLASS], {
            stdio: ['pipe', 'pipe', 'pipe'],
        });
        proc.stderr.on('data', (d: Buffer) => output.append(d.toString()));
        proc.on('error', (e) =>
            output.appendLine(`[error] failed to spawn ${javaPath}: ${e.message}`));
        return { writer: proc.stdin, reader: proc.stdout };
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'feng' }],
        outputChannel: output,
        synchronize: {
            fileEvents: vscode.workspace.createFileSystemWatcher('**/*.feng'),
        },
    };

    client = new LanguageClient('feng', 'Fēng Language Server', serverOptions, clientOptions);

    const trace = vscode.workspace
        .getConfiguration('feng.lsp').get<string>('trace.server', 'off');
    client.setTrace(traceToLevel(trace));

    context.subscriptions.push(client);
    await client.start();
}

export function deactivate(): Promise<void> | undefined {
    return client?.stop();
}

function traceToLevel(trace: string): Trace {
    switch (trace) {
        case 'messages': return Trace.Messages;
        case 'verbose': return Trace.Verbose;
        default: return Trace.Off;
    }
}

async function resolveServerJar(
    context: vscode.ExtensionContext,
    output: vscode.OutputChannel,
): Promise<string> {
    const cfg = vscode.workspace.getConfiguration('feng.lsp');
    const configured = cfg.get<string>('jarPath', '').trim();
    if (configured) {
        const p = expandHome(configured);
        if (!fs.existsSync(p)) {
            throw new Error(`configured JAR not found: ${p}`);
        }
        return p;
    }

    const serverDir = path.join(context.globalStorageUri.fsPath, 'server');
    fs.mkdirSync(serverDir, { recursive: true });
    const cached = path.join(serverDir, 'feng.jar');
    if (fs.existsSync(cached)) {
        return cached;
    }

    output.appendLine(`Downloading Fēng language server from ${GITHUB_REPO} releases...`);
    const url = await latestReleaseAssetUrl();
    await downloadFile(url, cached, output);
    return cached;
}

function latestReleaseAssetUrl(): Promise<string> {
    return new Promise((resolve, reject) => {
        const req = https.get(
            `https://api.github.com/repos/${GITHUB_REPO}/releases/latest`,
            {
                headers: {
                    'User-Agent': 'feng-vscode',
                    Accept: 'application/vnd.github+json',
                },
            },
            (res) => {
                if (res.statusCode !== 200) {
                    res.resume();
                    reject(new Error(`GitHub API responded HTTP ${res.statusCode}`));
                    return;
                }
                let body = '';
                res.on('data', (c) => (body += c));
                res.on('end', () => {
                    try {
                        const json = JSON.parse(body) as {
                            assets?: Array<{ name: string; browser_download_url: string }>;
                        };
                        const jar = (json.assets ?? []).find((a) => a.name.endsWith('.jar'));
                        if (!jar) {
                            reject(new Error('no .jar asset in the latest release'));
                            return;
                        }
                        resolve(jar.browser_download_url);
                    } catch (e) {
                        reject(e);
                    }
                });
            },
        );
        req.on('error', reject);
    });
}

function downloadFile(
    url: string,
    dest: string,
    output: vscode.OutputChannel,
): Promise<void> {
    return new Promise((resolve, reject) => {
        const follow = (u: string, redirects: number) => {
            if (redirects > 5) {
                reject(new Error('too many redirects while downloading'));
                return;
            }
            const req = https.get(u, { headers: { 'User-Agent': 'feng-vscode' } }, (res) => {
                if (res.statusCode && res.statusCode >= 300 && res.statusCode < 400
                    && res.headers.location) {
                    res.resume();
                    const loc = Array.isArray(res.headers.location)
                        ? res.headers.location[0] : res.headers.location;
                    follow(loc, redirects + 1);
                    return;
                }
                if (res.statusCode !== 200) {
                    res.resume();
                    reject(new Error(`download failed: HTTP ${res.statusCode}`));
                    return;
                }
                const tmp = `${dest}.part`;
                const file = fs.createWriteStream(tmp);
                res.pipe(file);
                file.on('finish', () => {
                    file.close();
                    fs.renameSync(tmp, dest);
                    output.appendLine(`Downloaded ${dest}`);
                    resolve();
                });
                file.on('error', reject);
            });
            req.on('error', reject);
        };
        follow(url, 0);
    });
}

function expandHome(p: string): string {
    if (p === '~' || p.startsWith('~/') || p.startsWith('~\\')) {
        return path.join(os.homedir(), p.slice(1));
    }
    return p;
}
