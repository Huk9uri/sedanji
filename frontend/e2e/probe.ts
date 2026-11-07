import { readFileSync } from 'node:fs';
import { createConnection } from 'node:net';

export interface Row { [key: string]: string | number | null }
export interface Snapshot {
  restaurants: Row[]; chefs: Row[]; mentions: Row[]; drafts: Row[];
  audits: Row[]; receipts: Row[]; candidates: Row[];
}
export function runtime(): { password: string; probePort: number; capability: string } {
  return JSON.parse(readFileSync(process.env.SEDANJI_E2E_DIR! + '/ready.json', 'utf8'));
}
export async function probe(operation = 'snapshot'): Promise<Snapshot> {
  const { probePort, capability } = runtime();
  return new Promise((resolve, reject) => {
    const socket = createConnection({ host: '127.0.0.1', port: probePort });
    let response = '';
    socket.setTimeout(5000, () => socket.destroy(new Error('Probe timeout')));
    socket.on('connect', () => socket.write(JSON.stringify({ operation, capability }) + '\n'));
    socket.on('data', data => { response += data.toString(); });
    socket.on('end', () => { try { resolve(JSON.parse(response)); } catch { reject(new Error('Invalid probe response')); } });
    socket.on('error', () => reject(new Error('Probe unavailable')));
  });
}
