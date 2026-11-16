import type { Metadata } from 'next';
export const metadata:Metadata={title:'Tmailer',description:'Your email, on the web and desktop.',icons:{icon:'/logo.svg'}};
export default function Layout({children}:{children:React.ReactNode}){return <html lang="en"><body>{children}</body></html>}
