var tablename = tablename = document.properties["id:tablename"];
var recordid = recordid = document.properties["id:recordid"];
var subfolder1 = "";
var subfolder2 = "";

// check 
if (tablename != null)
{
  subfolder1 = space.childByNamePath(tablename);
  if ( subfolder1 == null)
  {
  // Create first level folder (tablename)
   subfolder1 = space.createFolder(tablename);
  }

  // Move itself to new folder
  document.move(subfolder1);

  // Create second level folder (recordid)
  subfolder2 = subfolder1.childByNamePath(recordid); 
  if ( subfolder2 == null)
  {
     subfolder2 = subfolder1.createFolder(recordid);
  }
  // Move itself to new folder
  document.move(subfolder2);
}