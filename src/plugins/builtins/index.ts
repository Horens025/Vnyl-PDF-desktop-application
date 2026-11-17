import type { VnylPlugin } from '../types';
import { wordCountPlugin } from './wordCount';

/** Plugins that ship with Vnyl and are activated on startup. */
export const builtinPlugins: VnylPlugin[] = [wordCountPlugin];
