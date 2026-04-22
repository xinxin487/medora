import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./lib/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          ink: "#0f172a",
          teal: "#0f766e",
          mist: "#ecfeff",
          line: "#cbd5e1",
        },
      },
    },
  },
  plugins: [],
};

export default config;
