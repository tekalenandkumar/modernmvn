import type { Metadata, Viewport } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { Analytics } from "@vercel/analytics/react";
import { SpeedInsights } from "@vercel/speed-insights/next";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: {
    default: 'modernmvn — Maven Dependency Intelligence',
    template: '%s | modernmvn',
  },
  description: 'Modern Maven is a dependency intelligence tool for Java developers. Visualize dependency trees, detect conflicts, browse artifact metadata, and analyze POM files.',
  keywords: ['maven', 'java', 'dependency', 'artifact', 'pom', 'gradle', 'spring boot', 'dependency management'],
  icons: {
    icon: [
      { url: '/favicon.svg', type: 'image/svg+xml' },
      { url: '/favicon.png', type: 'image/png', sizes: '512x512' },
    ],
    apple: { url: '/favicon.png', sizes: '512x512', type: 'image/png' },
    shortcut: '/favicon.svg',
  },

  openGraph: {
    type: 'website',
    siteName: 'Modern Maven',
    title: 'Modern Maven — Dependency Intelligence for Java',
    description: 'Visualize dependency trees, detect conflicts, and browse Maven artifacts.',
  },
};

export const viewport: Viewport = {
  themeColor: '#090909',
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased`}
      >
        {children}
        <Analytics />
        <SpeedInsights />
      </body>
    </html>
  );
}
