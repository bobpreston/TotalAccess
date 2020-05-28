
BEGIN {

	# Use REST and a multidimensional array to get social media information:


	# Tell compiler that this is an array.
	temp2[0];

	loadArray(http, "rest_example.xml");


	# Users
	http["httpUrl"] = "https://jsonplaceholder.typicode.com/users";
	loadRestNodeArray(http, "/_json/_array/*", temp);
	for (i in temp) {
		id = getXmlNodeValue(getXmlNode(temp[i], "id/text()"));
		users[id, "username"] = getXmlNode(temp[i], "username/text()");
		users[id, "name"] = getXmlNode(temp[i], "name/text()");
		userCount++;
	}


	# Posts and comments
	http["httpUrl"] = "https://jsonplaceholder.typicode.com/posts";
	loadRestNodeArray(http, "/_json/_array/*", temp);
	for (i in temp) {
		userId = getXmlNodeValue(getXmlNode(temp[i], "userId/text()"));
		users[userId, "postCount"]++;
		
		postId = getXmlNodeValue(getXmlNode(temp[i], "id/text()"));
		http["httpUrl"] = "https://jsonplaceholder.typicode.com/comments?postId=" postId;
		loadRestNodeArray(http, "/_json/_array/*", temp2);
		users[userId, "commentCount"] += arrayLength(temp2);
	}

	for (i = 1; i <= userCount; i++) {
		print users[i, "name"];
		print users[i, "username"];
		print users[i, "postCount"];
		print users[i, "commentCount"];
		print "";
	}
}