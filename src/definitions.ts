export interface HyperswitchPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
