import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
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
    default: 'Modern Maven — Dependency Intelligence for Java',
    template: '%s | Modern Maven',
  },
  description: 'Modern Maven is a dependency intelligence tool for Java developers. Visualize dependency trees, detect conflicts, browse artifact metadata, and analyze POM files.',
  keywords: ['maven', 'java', 'dependency', 'artifact', 'pom', 'gradle', 'spring boot', 'dependency management'],
  openGraph: {
    type: 'website',
    siteName: 'Modern Maven',
    title: 'Modern Maven — Dependency Intelligence for Java',
    description: 'Visualize dependency trees, detect conflicts, and browse Maven artifacts.',
  },
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
      </body>
    </html>
  );
}
