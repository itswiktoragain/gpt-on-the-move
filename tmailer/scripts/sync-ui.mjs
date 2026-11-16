import{mkdir,copyFile,readdir}from'node:fs/promises';
await mkdir('public',{recursive:true});
for(const item of await readdir('ui',{withFileTypes:true}))if(item.isFile()&&!['index.html','runtime-config.js'].includes(item.name))await copyFile('ui/'+item.name,'public/'+item.name);
