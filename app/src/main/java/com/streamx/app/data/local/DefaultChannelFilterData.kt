package com.streamx.app.data.local

object DefaultChannelFilterData {

    const val RAW_CHANNEL_FILTER_JSON = """
{
  "code": 0,
  "data": {
    "typeList": [
      {
        "channelId": 1,
        "channelName": "Movie",
        "items": [
          {
            "filterType": "genre",
            "filterVals": [],
            "filterValsV2": [
              {
                "id": "All",
                "name": "All"
              },
              {
                "id": "Action",
                "name": "Action"
              },
              {
                "id": "Adventure",
                "name": "Adventure"
              },
              {
                "id": "Animation",
                "name": "Animation"
              },
              {
                "id": "Biography",
                "name": "Biography"
              },
              {
                "id": "Comedy",
                "name": "Comedy"
              },
              {
                "id": "Crime",
                "name": "Crime"
              },
              {
                "id": "Documentary",
                "name": "Documentary"
              },
              {
                "id": "Drama",
                "name": "Drama"
              },
              {
                "id": "Family",
                "name": "Family"
              },
              {
                "id": "Fantasy",
                "name": "Fantasy"
              },
              {
                "id": "Film-Noir",
                "name": "Film-Noir"
              },
              {
                "id": "Game-Show",
                "name": "Game-Show"
              },
              {
                "id": "History",
                "name": "History"
              },
              {
                "id": "Horror",
                "name": "Horror"
              },
              {
                "id": "Music",
                "name": "Music"
              },
              {
                "id": "Musical",
                "name": "Musical"
              },
              {
                "id": "Mystery",
                "name": "Mystery"
              },
              {
                "id": "News",
                "name": "News"
              },
              {
                "id": "Reality-TV",
                "name": "Reality-TV"
              },
              {
                "id": "Romance",
                "name": "Romance"
              },
              {
                "id": "Sci-Fi",
                "name": "Sci-Fi"
              },
              {
                "id": "Short",
                "name": "Short"
              },
              {
                "id": "Sport",
                "name": "Sport"
              },
              {
                "id": "Talk-Show",
                "name": "Talk-Show"
              },
              {
                "id": "Thriller",
                "name": "Thriller"
              },
              {
                "id": "War",
                "name": "War"
              },
              {
                "id": "Western",
                "name": "Western"
              },
              {
                "id": "Other",
                "name": "Other"
              }
            ],
            "rangeVals": null,
            "showOut": true,
            "title": "Genre"
          },
          {
            "filterType": "country",
            "filterVals": [],
            "filterValsV2": [
              {
                "id": "All",
                "name": "All"
              },
              {
                "id": "United States",
                "name": "United States"
              },
              {
                "id": "United Kingdom",
                "name": "United Kingdom"
              },
              {
                "id": "Korea",
                "name": "Korea"
              },
              {
                "id": "Japan",
                "name": "Japan"
              },
              {
                "id": "Bangladesh",
                "name": "Bangladesh"
              },
              {
                "id": "China",
                "name": "China"
              },
              {
                "id": "Egypt",
                "name": "Egypt"
              },
              {
                "id": "France",
                "name": "France"
              },
              {
                "id": "Germany",
                "name": "Germany"
              },
              {
                "id": "India",
                "name": "India"
              },
              {
                "id": "Indonesia",
                "name": "Indonesia"
              },
              {
                "id": "Iraq",
                "name": "Iraq"
              },
              {
                "id": "Italy",
                "name": "Italy"
              },
              {
                "id": "Ivory Coast",
                "name": "Ivory Coast"
              },
              {
                "id": "Kenya",
                "name": "Kenya"
              },
              {
                "id": "Lebanon",
                "name": "Lebanon"
              },
              {
                "id": "Mexico",
                "name": "Mexico"
              },
              {
                "id": "Morocco",
                "name": "Morocco"
              },
              {
                "id": "Nigeria",
                "name": "Nigeria"
              },
              {
                "id": "Pakistan",
                "name": "Pakistan"
              },
              {
                "id": "Philippines",
                "name": "Philippines"
              },
              {
                "id": "Russia",
                "name": "Russia"
              },
              {
                "id": "Saudi Arabia",
                "name": "Saudi Arabia"
              },
              {
                "id": "South Africa",
                "name": "South Africa"
              },
              {
                "id": "Spain",
                "name": "Spain"
              },
              {
                "id": "Syria",
                "name": "Syria"
              },
              {
                "id": "Thailand",
                "name": "Thailand"
              },
              {
                "id": "Malaysia",
                "name": "Malaysia"
              },
              {
                "id": "Turkey",
                "name": "Turkey"
              },
              {
                "id": "Other",
                "name": "Other"
              }
            ],
            "rangeVals": null,
            "showOut": true,
            "title": "Country"
          },
          {
            "filterType": "year",
            "filterVals": [],
            "filterValsV2": [
              {
                "id": "All",
                "name": "All"
              },
              {
                "id": "2026",
                "name": "2026"
              },
              {
                "id": "2025",
                "name": "2025"
              },
              {
                "id": "2024",
                "name": "2024"
              },
              {
                "id": "2023",
                "name": "2023"
              },
              {
                "id": "2022",
                "name": "2022"
              },
              {
                "id": "2021",
                "name": "2021"
              },
              {
                "id": "2020",
                "name": "2020"
              },
              {
                "id": "2010s",
                "name": "2010s"
              },
              {
                "id": "2000s",
                "name": "2000s"
              },
              {
                "id": "1990s",
                "name": "1990s"
              },
              {
                "id": "1980s",
                "name": "1980s"
              },
              {
                "id": "Other",
                "name": "Other"
              }
            ],
            "rangeVals": null,
            "showOut": true,
            "title": "Year"
          },
          {
            "filterType": "sort",
            "filterVals": [],
            "filterValsV2": [
              {
                "id": "ForYou",
                "name": "ForYou"
              },
              {
                "id": "Hottest",
                "name": "Hottest"
              },
              {
                "id": "Latest",
                "name": "Latest"
              }
            ],
            "rangeVals": null,
            "showOut": false,
            "title": "Sort by"
          },
          {
            "filterType": "rate",
            "filterVals": [],
            "filterValsV2": [],
            "rangeVals": {
              "maxVal": "10",
              "minVal": "0"
            },
            "showOut": false,
            "title": "Rating"
          },
          {
            "filterType": "classify",
            "filterVals": [],
            "filterValsV2": [
              {
                "id": "All",
                "name": "All"
              },
              {
                "id": "Original Audio",
                "name": "Original Audio"
              },
              {
                "id": "English dub",
                "name": "English dub"
              },
              {
                "id": "French dub",
                "name": "French dub"
              },
              {
                "id": "Hindi dub",
                "name": "Hindi dub"
              },
              {
                "id": "Bengali dub",
                "name": "Bengali dub"
              },
              {
                "id": "Urdu dub",
                "name": "Urdu dub"
              },
              {
                "id": "Punjabi dub",
                "name": "Punjabi dub"
              },
              {
                "id": "Tamil dub",
                "name": "Tamil dub"
              },
              {
                "id": "Telugu dub",
                "name": "Telugu dub"
              },
              {
                "id": "Malayalam dub",
                "name": "Malayalam dub"
              },
              {
                "id": "Kannada dub",
                "name": "Kannada dub"
              },
              {
                "id": "Arabic dub",
                "name": "Arabic dub"
              },
              {
                "id": "Arabic sub",
                "name": "Arabic sub"
              },
              {
                "id": "Tagalog dub",
                "name": "Tagalog dub"
              },
              {
                "id": "Indonesian dub",
                "name": "Indonesian dub"
              },
              {
                "id": "Russian dub",
                "name": "Russian dub"
              },
              {
                "id": "Kurdish sub",
                "name": "Kurdish sub"
              },
              {
                "id": "Spanish dub",
                "name": "Spanish dub"
              },
              {
                "id": "Spanish sub",
                "name": "Spanish sub"
              },
              {
                "id": "SpanishLatam dub",
                "name": "SpanishLatam dub"
              }
            ],
            "rangeVals": null,
            "showOut": false,
            "title": "Language"
          }
        ],
        "style": {
          "colNum": 3,
          "shape": "Vertical"
        },
        "subjectType": 1
      }
    ]
  },
  "message": "ok"
}
"""
}
