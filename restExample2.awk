
BEGIN {

	# Use REST and an array of arrays to get social media information:


	loadArray(http, "rest_example.xml");


	# Users
	http["httpUrl"] = "https://jsonplaceholder.typicode.com/users";
	loadRestNodeArray(http, "/_json/_array/_record", temp);
	for (i in temp) {
		id = getXmlNodeValue(getXmlNode(temp[i], "id/text()"));
		user["username"] = getXmlNode(temp[i], "username/text()");
		user["name"] = getXmlNode(temp[i], "name/text()");
		
		users[id] = cloneArray(user);
	}


	# Posts and comments
	http["httpUrl"] = "https://jsonplaceholder.typicode.com/posts";
	loadRestNodeArray(http, "/_json/_array/_record", temp);
	for (i in temp) {
		userId = getXmlNodeValue(getXmlNode(temp[i], "userId/text()"));
		user = users[userId];
		user["postCount"]++;
		
		postId = getXmlNodeValue(getXmlNode(temp[i], "id/text()"));
		http["httpUrl"] = "https://jsonplaceholder.typicode.com/comments?postId=" postId;
		loadRestNodeArray(http, "/_json/_array/_record", temp2);
		for (j in temp2) {
			users[userId]["commentCount"]++;
		}
	}

	
	for (i in users) {
		user = users[i];
		print "name =", user["name"];
		print "username =", user["username"];
		print "post count =", user["postCount"];
		print "comments for post(s) = ", user["commentCount"];
		print "";
		
	}
}

