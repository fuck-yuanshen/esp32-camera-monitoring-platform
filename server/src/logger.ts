function stamp(): string {
  return new Date().toISOString();
}

export const logger = {
  info(message: string, fields?: Record<string, unknown>) {
    console.log(format("INFO", message, fields));
  },
  warn(message: string, fields?: Record<string, unknown>) {
    console.warn(format("WARN", message, fields));
  },
  error(message: string, fields?: Record<string, unknown>) {
    console.error(format("ERROR", message, fields));
  }
};

function format(level: string, message: string, fields?: Record<string, unknown>): string {
  const suffix = fields ? ` ${JSON.stringify(fields)}` : "";
  return `[${stamp()}] ${level} ${message}${suffix}`;
}
