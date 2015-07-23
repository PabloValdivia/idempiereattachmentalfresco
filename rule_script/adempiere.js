// Create first level folder (tablename)
var subfolder1 = space.childByNamePath(document.properties["ad:tablename"]);
if ( subfolder1== null)
{
   subfolder1 = space.createFolder(document.properties["ad:tablename"]);
}

// Move itself to new folder
document.move(subfolder1);

// Create second level folder (recordid)
var subfolder2 = subfolder1.childByNamePath(document.properties["ad:recordid"]);
if ( subfolder2 == null)
{
   subfolder2 = subfolder1.createFolder(document.properties["ad:recordid"]);
}

// Move itself to new folder
document.move(subfolder2);